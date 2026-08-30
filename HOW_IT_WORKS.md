# SafeLink — How It Works

A step-by-step guide to every major flow in the SafeLink system.

---

## 1. ESP32 Boot Sequence

When you power on the Xiao ESP32S3, the following happens in order:

```
Power ON
  │
  ▼
Serial begins (115200 baud)
  │
  ▼
┌─────────────────────────────────────┐
│     AUTOMATIC RELAY DETECTION       │
│                                     │
│  For D0, D1, D2, D3:               │
│    Set pin → INPUT_PULLUP           │
│    Wait 20ms                        │
│    Read pin:                        │
│      LOW  → relay module present    │
│      HIGH → pin is floating (empty) │
│  Build activeRelays[] array         │
└─────────────────────────────────────┘
  │
  ▼
Connect to WiFi (max 15s → restart if failed)
  │
  ▼
Start mDNS → "safelink.local"
  │
  ▼
Enable OTA updates
  │
  ▼
Open UDP socket on port 8888
  │
  ▼
Start async HTTP server on port 80
  │
  ▼
Serial prints: "=== Device ready ==="
  │
  ▼
Enter loop() → wait for commands
```

---

## 2. Device Discovery

**How the Android app finds your ESP32 on the local network.**

### Step-by-Step

```
Android App                              ESP32 (192.168.1.x)
     │                                          │
     │  1. User taps "Scan Network" (or + icon) │
     │                                          │
     │  2. UDP broadcast to 255.255.255.255:8888│
     │     Payload: "DISCOVER_SAFELINK:123456"  │
     │─────────────────────────────────────────▶│
     │                                          │
     │                          3. ESP32 receives packet
     │                             Compares "123456" with PAIRING_KEY
     │                             ✅ Match → build JSON response
     │                             ❌ No match → silently ignore
     │                                          │
     │  4. ESP32 sends UDP response to App's IP │
     │◀─────────────────────────────────────────│
     │     JSON: { deviceId, deviceName,        │
     │             ip, port, relayCount,        │
     │             relays: [{name, state}...] } │
     │                                          │
     │  5. App deserializes JSON into           │
     │     SafeLinkDevice + Relay[] models      │
     │                                          │
     │  6. UI updates: device card appears      │
     │     with one control card per relay      │
```

### Discovery Timeout
- The app listens for 3 seconds (configurable via `timeoutMs`)
- After timeout, `isDiscovering` becomes `false` and the device list is finalized
- Multiple devices can respond — each gets its own card on the Home screen

---

## 3. Relay Toggle

**How tapping a toggle in the app actually switches a physical relay.**

### Step-by-Step

```
User taps Light toggle (currently OFF)
     │
     ▼
HomeViewModel.toggleRelay(device, relay)
     │
     ├─── Optimistic Update (immediate)
     │    UI shows relay as ON instantly
     │    (no network wait — feels instant)
     │
     └─── Launch background coroutine
               │
               ▼
          RelayApiService.toggleRelay(
               ip   = "192.168.1.105",
               port = 80,
               name = "Light",
               state = true          ← desired new state
          )
               │
               ▼
          HTTP POST http://192.168.1.105:80/api/relay/toggle
          Body: relayName=Light&state=1
               │
               │
          ┌────┴─────────────────────────────────┐
          │  ESP32 receives POST request          │
          │  Finds relay named "Light"            │
          │  Calls: digitalWrite(D0, HIGH)        │
          │  Physical relay clicks ON ⚡           │
          │  Returns: {"success":true,            │
          │            "relayName":"Light",       │
          │            "state":true}              │
          └────┬─────────────────────────────────┘
               │
          ┌────▼──────────────────┐
          │  HTTP 200 OK          │──▶ Keep optimistic state ✅
          └───────────────────────┘
               OR
          ┌────▼──────────────────┐
          │  Timeout / Error      │──▶ Roll back to OFF ↩
          └───────────────────────┘     Show error message
```

---

## 4. Settings & Pairing Key Persistence

**How settings survive app restarts.**

```
User types "abc123" in Pairing Key field
     │
     ▼
SettingsViewModel.updatePairingKey("abc123")
     │
     ▼
SettingsRepository.updatePairingKey("abc123")
     │
     ▼
DataStore writes "pairing_key" → "abc123"
     to: /data/data/com.safelink.app/files/settings.preferences_pb
     │
     (App closed and reopened)
     │
     ▼
DataStore reads "pairing_key" → "abc123"
     │
     ▼
SettingsViewModel.uiState emits SettingsState(pairingKey = "abc123")
     │
     ▼
MainScreen collects settingsState.pairingKey
     │
     ▼
discoverDevices(pairingKey = "abc123") ← correct key used automatically
```

---

## 5. Dark Mode Switching

**How the Dark Mode toggle changes the entire app theme instantly.**

```
User toggles Dark Mode switch ON
     │
     ▼
SettingsViewModel.toggleDarkMode(true)
     │
     ▼
DataStore persists "dark_mode_enabled" = true
     │
     ▼
SettingsViewModel.uiState Flow emits new SettingsState
     │
     ▼
MainActivity.darkModeEnabled (collectAsState) receives true
     │
     ▼
SafeLinkTheme(darkTheme = true) recomposes
     │
     ▼
Entire app re-renders with dark color scheme ← instant, no restart
```

---

## 6. OTA Firmware Update

**How to update the ESP32 firmware without a USB cable.**

```
1. ESP32 running with OTA enabled (after initial USB flash)

2. In PlatformIO, add to platformio.ini:
   upload_protocol = espota
   upload_port = safelink.local   ← or the IP address

3. Run: pio run --target upload
     │
     ▼
PlatformIO connects to ESP32 via mDNS/IP
     │
     ▼
ArduinoOTA.handle() in loop() accepts the transfer
     │
     ▼
ESP32 writes new firmware to flash
     │
     ▼
Auto-restart with new firmware ✅
```

---

## 7. Multiple Device Support

SafeLink supports **multiple ESP32 devices** on the same network.

```
UDP Broadcast → 255.255.255.255:8888

     ESP32 "Living Room" → responds with 2 relays
     ESP32 "Bedroom"     → responds with 4 relays
     ESP32 "Kitchen"     → responds with 1 relay
             │
             ▼
  App collects all 3 responses during 3s window
             │
             ▼
  Home screen shows 3 device cards
  Each card shows relay count and controls
             │
             ▼
  Tap any device → Device Detail screen
  Shows relay control cards based on that device's relay list
```

---

## 8. Relay Auto-Detection Logic

**How the ESP32 decides which pins have relays attached.**

Most relay modules use an optocoupler on the input side. When a relay module is connected to a GPIO pin:
- The module's input LED draws current through the optocoupler
- This creates a low-impedance path pulling the GPIO pin toward `GND`
- When `INPUT_PULLUP` is active, a connected relay reads `LOW`
- A floating (unconnected) pin reads `HIGH`

```
Pin D0 with INPUT_PULLUP:

  ESP32 internal          Relay module
  ┌──────────────┐        ┌─────────────────┐
  │   VCC (3.3V) │        │                 │
  │      │       │        │   ┌─────────┐   │
  │   10kΩ pullup│        │   │ Input   │   │
  │      │       │        │   │ LED     │   │
  │  D0 ──────────────────┼───│ (+)     │   │
  │      │       │        │   │ (-)─GND │   │
  │      │       │        │   └─────────┘   │
  │   Pin reads: │        └─────────────────┘
  │     LOW ✅   │ ← relay connected
  └──────────────┘

Without relay: pin floats HIGH → no relay registered
```
