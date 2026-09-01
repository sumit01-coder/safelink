/**
 * SafeLink Firmware — Seeed Studio Xiao ESP32S3
 *
 * Features:
 *  - Auto-detects connected relay modules on GPIO pins at boot
 *  - Secure UDP discovery with pairing key authentication
 *  - Async HTTP API for relay toggle and status
 *  - OTA (Over-The-Air) firmware updates
 *  - mDNS hostname (safelink.local)
 *  - Automatic WiFi reconnection
 *  - /api/status endpoint for health checks
 *
 * USAGE:
 *  1. Set WIFI_SSID, WIFI_PASS, and PAIRING_KEY below
 *  2. Flash using PlatformIO: `pio run --target upload`
 *  3. Enter same PAIRING_KEY in Android App → Settings → Pairing Key
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>
#include <ESPmDNS.h>
#include <ArduinoOTA.h>
#include <NimBLEDevice.h>
#include <Preferences.h>

Preferences preferences;

// ─────────────────────────────────────────────────────────────
// USER CONFIGURATION — Edit these before flashing
// ─────────────────────────────────────────────────────────────
static const char* AP_SSID      = "SafeLink";        // Hotspot name phones will see
static const char* AP_PASS      = "safelink123";     // Hotspot password (min 8 chars)
static const char* PAIRING_KEY  = "123456";          // Must match the Android App
static const char* DEVICE_NAME  = "Xiao ESP32S3 Hub";
static const char* HOSTNAME     = "safelink";        // Access via safelink.local
static const char* FIRMWARE_VER = "1.5.0";

// In AP mode, ESP32 always gets this fixed IP:
static const IPAddress AP_IP(192, 168, 4, 1);
static const IPAddress AP_SUBNET(255, 255, 255, 0);

// ─────────────────────────────────────────────────────────────
// Hardware — GPIO pins to probe for relay modules
// Adjust for your wiring. Xiao ESP32S3 usable pins: D0-D9
// ─────────────────────────────────────────────────────────────
static const uint8_t PROBE_PINS[]  = {D0, D1, D2, D3, D4, D5, D6};
static const uint8_t NUM_PROBE     = sizeof(PROBE_PINS);
static const uint8_t MAX_RELAYS    = 8;  // Maximum supported relays

// ─────────────────────────────────────────────────────────────
// Network constants
// ─────────────────────────────────────────────────────────────
static const uint16_t UDP_PORT        = 8888;
static const uint16_t HTTP_PORT       = 80;
static const uint32_t WIFI_TIMEOUT_MS = 15000;  // 15s connection timeout
static const uint32_t WIFI_RETRY_MS   = 5000;   // 5s between reconnect attempts

// ─────────────────────────────────────────────────────────────
// Relay descriptor — use fixed-size char arrays to avoid heap
// fragmentation caused by Arduino String on embedded devices
// ─────────────────────────────────────────────────────────────
struct RelayDescriptor {
    uint8_t  pin;
    char     name[24];
    bool     state;       // true = ON, false = OFF
};

static RelayDescriptor activeRelays[MAX_RELAYS];
static uint8_t         activeRelayCount = 0;

// ─────────────────────────────────────────────────────────────
// Global service objects
// ─────────────────────────────────────────────────────────────
static WiFiUDP        udp;
static AsyncWebServer server(HTTP_PORT);

// ─────────────────────────────────────────────────────────────
// Forward declarations
// ─────────────────────────────────────────────────────────────
void setupHardware();
void setupWiFi();
void setupMDNS();
void setupOTA();
void setupBLE();
void setupHTTP();
void handleUdpDiscovery();
void buildStatusJson(char* buf, size_t bufLen);
bool setRelay(uint8_t index, bool state);
int  findRelayByName(const char* name);
void reconnectWiFiIfNeeded();

// ─────────────────────────────────────────────────────────────
// Relay names assigned by index (customize as needed)
// ─────────────────────────────────────────────────────────────
static const char* RELAY_NAMES[] = {
    "Light 1", "Light 2", "Light 3", "Light 4",
    "Light 5", "Light 6", "Light 7", "Light 8"
};

// ─────────────────────────────────────────────────────────────
// Hardware Setup — Auto-detect connected relay modules
// ─────────────────────────────────────────────────────────────
void setupHardware() {
    Serial.println("=== Advanced Automatic Relay Detection ===");
    // Do not reset activeRelayCount here, so this can be called repeatedly
    
    uint8_t previousCount = activeRelayCount;

    const uint8_t SAMPLES_PER_PIN = 10;
    const uint8_t HIGH_VOTES_NEEDED = 8;
    const uint16_t SAMPLE_GAP_MS = 4;
    const uint16_t MODE_SETTLE_MS = 15;

    for (uint8_t i = 0; i < NUM_PROBE && activeRelayCount < MAX_RELAYS; i++) {
        uint8_t pin = PROBE_PINS[i];

        // Skip pins that are already active
        bool alreadyActive = false;
        for(uint8_t j = 0; j < activeRelayCount; j++) {
            if(activeRelays[j].pin == pin) {
                alreadyActive = true;
                break;
            }
        }
        if (alreadyActive) continue;

        // --- Step 1 (decisive): pull DOWN internally, see if it holds ---
        pinMode(pin, INPUT_PULLDOWN);
        delay(MODE_SETTLE_MS);

        uint8_t highUnderPulldown = 0;
        for (uint8_t s = 0; s < SAMPLES_PER_PIN; s++) {
            if (digitalRead(pin) == HIGH) highUnderPulldown++;
            delay(SAMPLE_GAP_MS);
        }

        // Decisive test: if the pin STILL reads HIGH most of the time even
        // while the internal pull-down is actively fighting it, something
        // external with a stronger pull (the relay module's onboard
        // pull-up) must be attached.
        bool relayDetected = (highUnderPulldown >= HIGH_VOTES_NEEDED);

        if (relayDetected) {
            uint8_t idx = activeRelayCount;
            activeRelays[idx].pin   = pin;
            activeRelays[idx].state = false;
            strncpy(activeRelays[idx].name, RELAY_NAMES[idx], sizeof(activeRelays[idx].name) - 1);
            activeRelays[idx].name[sizeof(activeRelays[idx].name) - 1] = '\0';

            // Restore state from Preferences
            preferences.begin("safelink", false);
            char keyName[16];
            snprintf(keyName, sizeof(keyName), "pin_%d", pin);
            bool savedState = preferences.getBool(keyName, false);
            preferences.end();
            
            activeRelays[idx].state = savedState;

            // Switch to output and ensure relay uses Active-Low logic (HIGH = OFF, LOW = ON)
            pinMode(pin, OUTPUT);
            digitalWrite(pin, savedState ? LOW : HIGH);

            Serial.printf("  [FOUND] Pin D%d → %s (votes: %u/%u) [Restored: %s]\n", i, activeRelays[idx].name, highUnderPulldown, SAMPLES_PER_PIN, savedState ? "ON" : "OFF");
            activeRelayCount++;
        } else {
            // Leave undetected pins as plain inputs
            pinMode(pin, INPUT);
        }
    }

    if (activeRelayCount > previousCount) {
        Serial.printf("=== %d relay(s) ready (added %d) ===\n", activeRelayCount, activeRelayCount - previousCount);
    }
}

// ─────────────────────────────────────────────────────────────
// WiFi — Start as Access Point (creates its own hotspot)
// ─────────────────────────────────────────────────────────────
void setupWiFi() {
    WiFi.mode(WIFI_AP);
    WiFi.softAPConfig(AP_IP, AP_IP, AP_SUBNET);
    // Force Tx Power to MAX (19.5dBm or 20dBm for stability)
    WiFi.setTxPower(WIFI_POWER_19_5dBm);
    // Start AP on channel 6 (less congested), max 4 connections, not hidden
    bool started = WiFi.softAP(AP_SSID, AP_PASS, 6, 0, 4);

    if (started) {
        Serial.printf("[OK] Hotspot started!\n");
        Serial.printf("     SSID    : %s\n", AP_SSID);
        Serial.printf("     Password: %s\n", AP_PASS);
        Serial.printf("     IP      : %s\n", WiFi.softAPIP().toString().c_str());
    } else {
        Serial.println("[ERROR] Failed to start AP! Restarting...");
        delay(1000);
        ESP.restart();
    }

    // Log when a device connects / disconnects
    WiFi.onEvent([](WiFiEvent_t event, WiFiEventInfo_t info) {
        uint8_t* mac = info.wifi_ap_staconnected.mac;
        Serial.printf("[AP] Client CONNECTED   MAC: %02X:%02X:%02X:%02X:%02X:%02X  (Clients: %d)\n",
                      mac[0], mac[1], mac[2], mac[3], mac[4], mac[5],
                      WiFi.softAPgetStationNum());
    }, ARDUINO_EVENT_WIFI_AP_STACONNECTED);

    WiFi.onEvent([](WiFiEvent_t event, WiFiEventInfo_t info) {
        uint8_t* mac = info.wifi_ap_stadisconnected.mac;
        Serial.printf("[AP] Client DISCONNECTED MAC: %02X:%02X:%02X:%02X:%02X:%02X  (Clients: %d)\n",
                      mac[0], mac[1], mac[2], mac[3], mac[4], mac[5],
                      WiFi.softAPgetStationNum());
    }, ARDUINO_EVENT_WIFI_AP_STADISCONNECTED);

    WiFi.onEvent([](WiFiEvent_t event, WiFiEventInfo_t info) {
        IPAddress ip(info.wifi_ap_staipassigned.ip.addr);
        Serial.printf("[AP] Client got IP      : %s  → Connect app now!\n", ip.toString().c_str());
    }, ARDUINO_EVENT_WIFI_AP_STAIPASSIGNED);
}


// ─────────────────────────────────────────────────────────────
// mDNS — advertise as "safelink.local"
// ─────────────────────────────────────────────────────────────
void setupMDNS() {
    if (MDNS.begin(HOSTNAME)) {
        MDNS.addService("http", "tcp", HTTP_PORT);
        Serial.printf("[OK] mDNS started: http://%s.local\n", HOSTNAME);
    } else {
        Serial.println("[WARN] mDNS failed to start");
    }
}

// ─────────────────────────────────────────────────────────────
// OTA — enable wireless firmware updates from PlatformIO/Arduino IDE
// ─────────────────────────────────────────────────────────────
void setupOTA() {
    ArduinoOTA.setHostname(HOSTNAME);
    ArduinoOTA.onStart([]() {
        Serial.println("[OTA] Starting update...");
    });
    ArduinoOTA.onProgress([](unsigned int progress, unsigned int total) {
        Serial.printf("[OTA] %u%%\r", progress * 100 / total);
    });
    ArduinoOTA.onEnd([]() {
        Serial.println("\n[OTA] Done! Rebooting.");
    });
    ArduinoOTA.onError([](ota_error_t error) {
        Serial.printf("[OTA] Error[%u]\n", error);
    });
    ArduinoOTA.begin();
    Serial.println("[OK] OTA ready");
}

// ─────────────────────────────────────────────────────────────
// Relay helpers
// ─────────────────────────────────────────────────────────────
bool setRelay(uint8_t index, bool state) {
    if (index >= activeRelayCount) return false;
    activeRelays[index].state = state;
    // Active-Low Logic: LOW = ON, HIGH = OFF
    digitalWrite(activeRelays[index].pin, state ? LOW : HIGH);
    
    // Save to memory
    preferences.begin("safelink", false);
    char keyName[16];
    snprintf(keyName, sizeof(keyName), "pin_%d", activeRelays[index].pin);
    preferences.putBool(keyName, state);
    preferences.end();
    
    Serial.printf("[RELAY] %s → %s\n", activeRelays[index].name, state ? "ON" : "OFF");
    return true;
}

int findRelayByName(const char* name) {
    for (uint8_t i = 0; i < activeRelayCount; i++) {
        if (strcmp(activeRelays[i].name, name) == 0) return i;
    }
    return -1;
}

// ─────────────────────────────────────────────────────────────
// Build JSON status blob (reused by /api/status and UDP response)
// ─────────────────────────────────────────────────────────────
void buildStatusJson(char* buf, size_t bufLen) {
    StaticJsonDocument<512> doc;
    doc["deviceId"]   = WiFi.softAPmacAddress();
    doc["deviceName"] = DEVICE_NAME;
    doc["ip"]         = WiFi.softAPIP().toString();
    doc["port"]       = HTTP_PORT;
    doc["relayCount"] = activeRelayCount;
    doc["firmware"]   = FIRMWARE_VER;
    doc["uptimeSec"]  = millis() / 1000;
    doc["rssi"]       = 0;  // N/A in AP mode

    JsonArray relays = doc.createNestedArray("relays");
    for (uint8_t i = 0; i < activeRelayCount; i++) {
        JsonObject r = relays.createNestedObject();
        r["id"]    = i + 1;
        r["name"]  = activeRelays[i].name;
        r["state"] = activeRelays[i].state;
    }
    serializeJson(doc, buf, bufLen);
}

// ─────────────────────────────────────────────────────────────
// HTTP API Setup
// ─────────────────────────────────────────────────────────────
void setupHTTP() {
    // GET /api/status — device health and relay states
    server.on("/api/status", HTTP_GET, [](AsyncWebServerRequest* req) {
        char buf[512];
        buildStatusJson(buf, sizeof(buf));
        AsyncWebServerResponse* res = req->beginResponse(200, "application/json", buf);
        res->addHeader("Access-Control-Allow-Origin", "*");
        res->addHeader("Cache-Control", "no-cache");
        req->send(res);
        Serial.printf("[HTTP] GET /api/status from %s\n", req->client()->remoteIP().toString().c_str());
    });

    // POST /api/relay/toggle — toggle relay by name or index
    // Body params: relayName=Light  OR  relayIndex=0  AND optionally state=1
    server.on("/api/relay/toggle", HTTP_POST, [](AsyncWebServerRequest* req) {
        int idx = -1;

        if (req->hasParam("relayName", true)) {
            const char* name = req->getParam("relayName", true)->value().c_str();
            idx = findRelayByName(name);
        } else if (req->hasParam("relayIndex", true)) {
            idx = req->getParam("relayIndex", true)->value().toInt();
        }

        if (idx < 0 || idx >= activeRelayCount) {
            req->send(400, "application/json", "{\"error\":\"Relay not found\"}");
            return;
        }

        // If explicit 'state' param is given, use it; otherwise toggle
        bool newState;
        if (req->hasParam("state", true)) {
            newState = req->getParam("state", true)->value().toInt() != 0;
        } else {
            newState = !activeRelays[idx].state;
        }

        setRelay((uint8_t)idx, newState);

        StaticJsonDocument<128> resp;
        resp["success"]   = true;
        resp["relayName"] = activeRelays[idx].name;
        resp["relayIndex"] = idx;
        resp["state"]     = activeRelays[idx].state;

        char buf[128];
        serializeJson(resp, buf, sizeof(buf));
        req->send(200, "application/json", buf);
    });

    // CORS preflight for potential web dashboard
    server.onNotFound([](AsyncWebServerRequest* req) {
        if (req->method() == HTTP_OPTIONS) {
            AsyncWebServerResponse* res = req->beginResponse(204);
            res->addHeader("Access-Control-Allow-Origin", "*");
            res->addHeader("Access-Control-Allow-Methods", "GET,POST");
            req->send(res);
        } else {
            req->send(404, "application/json", "{\"error\":\"Not found\"}");
        }
    });

    server.begin();
    Serial.printf("[OK] HTTP server on port %d\n", HTTP_PORT);
}

// ─────────────────────────────────────────────────────────────
// Auto-reconnect — not needed in AP mode, but kept as a no-op for loop() compatibility
void reconnectWiFiIfNeeded() {
    // In AP mode the ESP32 is the router, it never "disconnects"
}

// ─────────────────────────────────────────────────────────────
// UDP Discovery Handler — called every loop iteration
// ─────────────────────────────────────────────────────────────
void handleUdpDiscovery() {
    // Note: packetBuffer is 256 bytes; we read max 255 leaving room for null terminator
    static char packetBuffer[256];
    int packetSize = udp.parsePacket();
    if (packetSize <= 0) return;

    int len = udp.read(packetBuffer, sizeof(packetBuffer) - 1);
    if (len <= 0) return;
    packetBuffer[len] = '\0';  // Safe null-terminate (buffer is 256, max read is 255)

    // Expected: "DISCOVER_SAFELINK:<PAIRING_KEY>"
    char expectedPayload[64];
    snprintf(expectedPayload, sizeof(expectedPayload), "DISCOVER_SAFELINK:%s", PAIRING_KEY);

    if (strcmp(packetBuffer, expectedPayload) == 0) {
        Serial.printf("[UDP] Valid discovery from %s\n", udp.remoteIP().toString().c_str());

        char responseBuf[512];
        buildStatusJson(responseBuf, sizeof(responseBuf));

        udp.beginPacket(udp.remoteIP(), udp.remotePort());
        udp.print(responseBuf);
        udp.endPacket();
    } else if (strncmp(packetBuffer, "DISCOVER_SAFELINK", 17) == 0) {
        Serial.println("[UDP] Discovery with INVALID pairing key. Ignored.");
    }
}

// ─────────────────────────────────────────────────────────────
// BLE GATT Server Setup
// ─────────────────────────────────────────────────────────────
static const char* BLE_SERVICE_UUID = "a07498ca-1088-4361-9c3a-23d9a101fcc4";
static const char* BLE_COMMAND_CHAR_UUID = "d486d365-27a1-4ee6-85dc-b1187799d123";
static const char* BLE_STATUS_CHAR_UUID  = "c2e55725-b467-4d69-b5f7-669c3a37b420";

class BleCommandCallback : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string value = pCharacteristic->getValue();
        if (value.length() > 0) {
            Serial.printf("[BLE] Command received: %s\n", value.c_str());
            StaticJsonDocument<128> doc;
            DeserializationError error = deserializeJson(doc, value);
            if (!error) {
                if (doc.containsKey("relayIndex") && doc.containsKey("state")) {
                    int idx = doc["relayIndex"];
                    bool state = doc["state"];
                    setRelay(idx, state);
                }
            } else {
                Serial.println("[BLE] JSON Parse Error");
            }
        }
    }
};

class BleStatusCallback : public NimBLECharacteristicCallbacks {
    void onRead(NimBLECharacteristic* pCharacteristic) {
        char buf[512];
        buildStatusJson(buf, sizeof(buf));
        pCharacteristic->setValue((uint8_t*)buf, strlen(buf));
    }
};

void setupBLE() {
    NimBLEDevice::init(DEVICE_NAME);
    NimBLEServer *pServer = NimBLEDevice::createServer();
    NimBLEService *pService = pServer->createService(BLE_SERVICE_UUID);
    
    NimBLECharacteristic *pCommandChar = pService->createCharacteristic(
        BLE_COMMAND_CHAR_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR
    );
    pCommandChar->setCallbacks(new BleCommandCallback());

    NimBLECharacteristic *pStatusChar = pService->createCharacteristic(
        BLE_STATUS_CHAR_UUID, NIMBLE_PROPERTY::READ
    );
    pStatusChar->setCallbacks(new BleStatusCallback());
    
    pService->start();

    NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(BLE_SERVICE_UUID);
    
    std::string mData = "";
    mData += (char)0xFF; 
    mData += (char)0xFF;
    
    uint32_t ip = (uint32_t)WiFi.softAPIP();
    mData += (char)(ip & 0xFF);
    mData += (char)((ip >> 8) & 0xFF);
    mData += (char)((ip >> 16) & 0xFF);
    mData += (char)((ip >> 24) & 0xFF);
    
    mData += PAIRING_KEY;

    pAdvertising->setManufacturerData(mData);
    pAdvertising->setScanResponseData(NimBLEAdvertisementData());
    
    pAdvertising->setMinInterval(0x20);
    pAdvertising->setMaxInterval(0x40);
    
    pAdvertising->start();
    
    Serial.println("[OK] BLE GATT Server started");
}

// ─────────────────────────────────────────────────────────────
// Arduino setup()
// ─────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.printf("\n\n=== SafeLink Firmware %s ===\n", FIRMWARE_VER);

    setupHardware();
    setupWiFi();
    setupMDNS();
    setupOTA();
    setupBLE();

    udp.begin(UDP_PORT);
    Serial.printf("[OK] UDP listening on port %d\n", UDP_PORT);

    setupHTTP();

    Serial.println("\n=== Device ready ===");
}

// ─────────────────────────────────────────────────────────────
// Arduino loop()
// ─────────────────────────────────────────────────────────────
void loop() {
    reconnectWiFiIfNeeded();
    ArduinoOTA.handle();
    handleUdpDiscovery();

    // Background Hot-Plug Detection: Scan pins every 10 seconds
    static uint32_t lastHardwareScan = 0;
    if (millis() - lastHardwareScan > 10000) {
        lastHardwareScan = millis();
        setupHardware();
    }

    // Every 15 seconds, print how many clients are connected
    static uint32_t lastClientLog = 0;
    if (millis() - lastClientLog > 15000) {
        lastClientLog = millis();
        int n = WiFi.softAPgetStationNum();
        if (n > 0) {
            Serial.printf("[AP] %d client(s) connected to SafeLink hotspot\n", n);
        } else {
            Serial.println("[AP] Waiting for phone to connect to SafeLink Wi-Fi...");
        }
    }
}
