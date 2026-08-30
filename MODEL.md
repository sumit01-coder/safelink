# SafeLink — Data Models

All models used across the Android app and the ESP32 JSON protocol.

---

## Android Data Models

### `SafeLinkDevice`
**File**: `app/src/main/java/com/safelink/app/data/model/SafeLinkDevice.kt`

Represents a single discovered ESP32 device.

```kotlin
@Serializable
data class SafeLinkDevice(
    val type: String = "SAFELINK_DEVICE",  // Reserved for future filtering
    val deviceId: String,                  // MAC address of the ESP32
    val deviceName: String,                // Human-readable name (e.g. "Xiao ESP32S3 Hub")
    val ip: String,                        // Local IP address (e.g. "192.168.1.105")
    val port: Int = 80,                    // HTTP server port
    val relayCount: Int,                   // Number of active relays detected at boot
    val firmware: String = "1.0.0",        // Firmware version string

    // UI-only fields (not required in JSON, have defaults)
    val isOnline: Boolean = true,
    val wifiSignal: Int = -50,             // dBm (from ESP32 RSSI)
    val relays: List<Relay> = emptyList()  // List of relay descriptors
)
```

---

### `Relay`
**File**: `app/src/main/java/com/safelink/app/data/model/Relay.kt`

Represents a single relay channel on a device.

```kotlin
@Serializable
data class Relay(
    val id: Int = 0,       // Assigned locally by index after discovery (not in ESP32 JSON)
    val name: String,      // e.g. "Light", "Fan", "Relay 3"
    val state: Boolean     // true = ON, false = OFF
)
```

> **Note**: The ESP32 firmware does not include `id` in its JSON. The Android app assigns sequential IDs (1, 2, 3...) after parsing the response.

---

### `SettingsState`
**File**: `app/src/main/java/com/safelink/app/data/repository/SettingsRepository.kt`

Holds all user preferences, persisted in DataStore.

```kotlin
data class SettingsState(
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val pairingKey: String = "123456"      // Must match ESP32 PAIRING_KEY
)
```

#### DataStore Keys

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `notifications_enabled` | Boolean | `true` | Toggle notification alerts |
| `dark_mode_enabled` | Boolean | `false` | App-wide dark theme |
| `haptic_feedback_enabled` | Boolean | `true` | Vibrate on relay toggle |
| `pairing_key` | String | `"123456"` | Shared secret for device discovery |

---

### `HomeUiState`
**File**: `app/src/main/java/com/safelink/app/ui/home/HomeViewModel.kt`

The complete state of the Home screen.

```kotlin
data class HomeUiState(
    val devices: List<SafeLinkDevice> = emptyList(),  // Discovered devices
    val isDiscovering: Boolean = false,                // Scan in progress
    val error: String? = null                          // Error message to display
)
```

---

## ESP32 JSON Protocol

### Discovery Response (ESP32 → App)

Sent via UDP in response to a valid `DISCOVER_SAFELINK:<key>` broadcast.

```json
{
  "deviceId":   "A0:B1:C2:D3:E4:F5",
  "deviceName": "Xiao ESP32S3 Hub",
  "ip":         "192.168.1.105",
  "port":       80,
  "relayCount": 2,
  "firmware":   "1.1.0",
  "uptimeSec":  3847,
  "rssi":       -52,
  "relays": [
    { "id": 1, "name": "Light", "state": false },
    { "id": 2, "name": "Fan",   "state": true  }
  ]
}
```

#### Field Reference

| Field | Type | Description |
|-------|------|-------------|
| `deviceId` | string | WiFi MAC address — globally unique |
| `deviceName` | string | Configurable name in firmware |
| `ip` | string | Local IP at time of response |
| `port` | int | HTTP API port (default: 80) |
| `relayCount` | int | Number of relays detected at boot |
| `firmware` | string | Firmware version |
| `uptimeSec` | int | Seconds since last boot |
| `rssi` | int | WiFi signal strength in dBm |
| `relays` | array | Per-relay state (see below) |

#### Relay Object

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | 1-based index |
| `name` | string | Friendly name (`"Light"`, `"Fan"`, etc.) |
| `state` | bool | `true` = ON, `false` = OFF |

---

### Relay Toggle Request (App → ESP32)

`POST http://<device-ip>/api/relay/toggle`

Content-Type: `application/x-www-form-urlencoded`

#### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `relayName` | Optional* | Name of relay to toggle (e.g. `"Light"`) |
| `relayIndex` | Optional* | 0-based index of relay |
| `state` | Optional | `1` = force ON, `0` = force OFF. Omit to toggle |

> *Either `relayName` OR `relayIndex` must be provided.

#### Example Requests

Toggle by name:
```
POST /api/relay/toggle
relayName=Light
```

Set relay 0 to ON explicitly:
```
POST /api/relay/toggle
relayIndex=0&state=1
```

#### Success Response

```json
{
  "success": true,
  "relayName": "Light",
  "relayIndex": 0,
  "state": true
}
```

#### Error Response

```json
{
  "error": "Relay not found"
}
```

---

### Status Request (App → ESP32)

`GET http://<device-ip>/api/status`

Returns the same JSON structure as the Discovery Response.

```json
{
  "deviceId":   "A0:B1:C2:D3:E4:F5",
  "deviceName": "Xiao ESP32S3 Hub",
  "ip":         "192.168.1.105",
  "port":       80,
  "relayCount": 2,
  "firmware":   "1.1.0",
  "uptimeSec":  3902,
  "rssi":       -52,
  "relays": [
    { "id": 1, "name": "Light", "state": true  },
    { "id": 2, "name": "Fan",   "state": false }
  ]
}
```

---

## Type Mapping: ESP32 C++ ↔ Android Kotlin

| C++ (firmware) | Kotlin (app) | Notes |
|----------------|-------------|-------|
| `const char*` | `String` | Auto JSON mapping |
| `bool` | `Boolean` | Direct |
| `uint8_t` | `Int` | Widened |
| `int` | `Int` | Direct |
| `char name[24]` | `String` | Fixed-size on ESP32, dynamic in Kotlin |
| `RelayDescriptor[]` | `List<Relay>` | JSON array |
| `WiFi.macAddress()` | `String (deviceId)` | Used as unique device identifier |
