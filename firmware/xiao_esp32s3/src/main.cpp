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

// ─────────────────────────────────────────────────────────────
// USER CONFIGURATION — Edit these before flashing
// ─────────────────────────────────────────────────────────────
static const char* WIFI_SSID   = "YOUR_WIFI_SSID";
static const char* WIFI_PASS   = "YOUR_WIFI_PASSWORD";
static const char* PAIRING_KEY = "123456";       // Must match the Android App
static const char* DEVICE_NAME = "Xiao ESP32S3 Hub";
static const char* HOSTNAME    = "safelink";     // Access via safelink.local
static const char* FIRMWARE_VER = "1.1.0";

// ─────────────────────────────────────────────────────────────
// Hardware — GPIO pins to probe for relay modules
// Adjust for your wiring. Xiao ESP32S3 usable pins: D0-D9
// ─────────────────────────────────────────────────────────────
static const uint8_t PROBE_PINS[]  = {D0, D1, D2, D3};
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
    "Light", "Fan", "Relay 3", "Relay 4",
    "Relay 5", "Relay 6", "Relay 7", "Relay 8"
};

// ─────────────────────────────────────────────────────────────
// Hardware Setup — Auto-detect connected relay modules
// ─────────────────────────────────────────────────────────────
void setupHardware() {
    Serial.println("=== Automatic Relay Detection ===");
    activeRelayCount = 0;

    for (uint8_t i = 0; i < NUM_PROBE && activeRelayCount < MAX_RELAYS; i++) {
        uint8_t pin = PROBE_PINS[i];

        // Pull pin HIGH via internal resistor. Most relay modules with an
        // optocoupler input will actively pull the line LOW when connected,
        // because their input transistor presents a low-impedance load.
        // If nothing is connected, the pin floats HIGH.
        pinMode(pin, INPUT_PULLUP);
        delay(20);  // Allow voltage to settle

        bool relayDetected = (digitalRead(pin) == LOW);

        if (relayDetected) {
            uint8_t idx = activeRelayCount;
            activeRelays[idx].pin   = pin;
            activeRelays[idx].state = false;
            strncpy(activeRelays[idx].name, RELAY_NAMES[idx], sizeof(activeRelays[idx].name) - 1);
            activeRelays[idx].name[sizeof(activeRelays[idx].name) - 1] = '\0';

            // Switch to output and ensure relay starts in OFF position
            pinMode(pin, OUTPUT);
            digitalWrite(pin, LOW);

            Serial.printf("  [FOUND] Pin D%d → %s\n", i, activeRelays[idx].name);
            activeRelayCount++;
        } else {
            Serial.printf("  [EMPTY] Pin D%d — no relay detected\n", i);
        }
    }

    // Fallback: ensure at least one relay is registered so the app can connect
    if (activeRelayCount == 0) {
        Serial.println("  [WARN]  No relays detected. Registering default on D0.");
        activeRelays[0].pin   = PROBE_PINS[0];
        activeRelays[0].state = false;
        strncpy(activeRelays[0].name, RELAY_NAMES[0], sizeof(activeRelays[0].name) - 1);
        activeRelays[0].name[sizeof(activeRelays[0].name) - 1] = '\0';
        pinMode(PROBE_PINS[0], OUTPUT);
        digitalWrite(PROBE_PINS[0], LOW);
        activeRelayCount = 1;
    }

    Serial.printf("=== %d relay(s) ready ===\n", activeRelayCount);
}

// ─────────────────────────────────────────────────────────────
// WiFi — connect with timeout
// ─────────────────────────────────────────────────────────────
void setupWiFi() {
    Serial.printf("Connecting to \"%s\" ", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASS);

    uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED) {
        if (millis() - start > WIFI_TIMEOUT_MS) {
            Serial.println("\n[ERROR] WiFi timeout! Restarting...");
            delay(1000);
            ESP.restart();
        }
        delay(500);
        Serial.print(".");
    }

    Serial.printf("\n[OK] Connected! IP: %s  RSSI: %d dBm\n",
                  WiFi.localIP().toString().c_str(), WiFi.RSSI());
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
    digitalWrite(activeRelays[index].pin, state ? HIGH : LOW);
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
    doc["deviceId"]   = WiFi.macAddress();
    doc["deviceName"] = DEVICE_NAME;
    doc["ip"]         = WiFi.localIP().toString();
    doc["port"]       = HTTP_PORT;
    doc["relayCount"] = activeRelayCount;
    doc["firmware"]   = FIRMWARE_VER;
    doc["uptimeSec"]  = millis() / 1000;
    doc["rssi"]       = WiFi.RSSI();

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
        req->send(200, "application/json", buf);
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
// Auto-reconnect if WiFi drops
// ─────────────────────────────────────────────────────────────
void reconnectWiFiIfNeeded() {
    static uint32_t lastRetry = 0;
    if (WiFi.status() != WL_CONNECTED && millis() - lastRetry > WIFI_RETRY_MS) {
        Serial.println("[WIFI] Connection lost. Reconnecting...");
        WiFi.reconnect();
        lastRetry = millis();
    }
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
}
