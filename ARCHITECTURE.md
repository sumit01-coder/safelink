# SafeLink — System Architecture

## Overview

SafeLink is a two-component system: an **Android application** and **ESP32S3 firmware**. They communicate exclusively over the **local WiFi network** — no cloud, no internet required.

```
┌─────────────────────────────────────────────────────────┐
│                   LOCAL WiFi NETWORK                     │
│                                                         │
│   ┌─────────────────┐          ┌──────────────────┐    │
│   │  Android Phone  │          │  Xiao ESP32S3    │    │
│   │                 │          │                  │    │
│   │  ┌───────────┐  │ UDP 8888 │  ┌────────────┐  │    │
│   │  │  SafeLink │──┼──────────┼─▶│ UDP Server │  │    │
│   │  │    App    │◀─┼──────────┼──│ (Discovery)│  │    │
│   │  └───────────┘  │          │  └────────────┘  │    │
│   │        │        │ HTTP :80 │  ┌────────────┐  │    │
│   │        └────────┼──────────┼─▶│ HTTP API   │  │    │
│   │                 │          │  └────────────┘  │    │
│   └─────────────────┘          │  ┌────────────┐  │    │
│                                │  │   Relays   │  │    │
│                                │  │ D0 D1 D2   │  │    │
│                                │  └────────────┘  │    │
│                                └──────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## Android Application Architecture

The app follows **MVVM (Model-View-ViewModel)** with unidirectional data flow.

```
┌─────────────────────────────────────────────────────────────┐
│                     Android App                              │
│                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────────┐  │
│  │  View    │    │  ViewModel   │    │   Repository /   │  │
│  │ (Compose)│◀───│  (StateFlow) │◀───│    Services      │  │
│  │          │───▶│              │───▶│                  │  │
│  └──────────┘    └──────────────┘    └──────────────────┘  │
│                                               │             │
│              ┌────────────────────────────────┤             │
│              │                                │             │
│   ┌──────────▼────────┐         ┌─────────────▼──────────┐ │
│   │  UdpDiscovery     │         │   RelayApiService      │ │
│   │  Service          │         │   (OkHttp HTTP POST)   │ │
│   │  (UDP Broadcast)  │         └────────────────────────┘ │
│   └───────────────────┘                                     │
│              │                  ┌─────────────────────────┐ │
│              │                  │   SettingsRepository    │ │
│              │                  │   (DataStore Prefs)     │ │
│              │                  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Layer Breakdown

#### 1. UI Layer — Jetpack Compose
All screens are stateless composables that receive data via `uiState` and emit events via callbacks.

| Screen | File | Description |
|--------|------|-------------|
| Home | `HomeScreen.kt` | Device list, discovery trigger |
| Device Detail | `DeviceDetailScreen.kt` | Per-relay circular sliders |
| Scenes | `ScenesScreen.kt` | One-tap automation presets |
| Stats | `StatsScreen.kt` | Network health dashboard |
| Settings | `SettingsScreen.kt` | Preferences, pairing key |

#### 2. ViewModel Layer
ViewModels hold and transform state. They are lifecycle-aware and survive screen rotations.

| ViewModel | Exposes | Handles |
|-----------|---------|---------|
| `HomeViewModel` | `HomeUiState` (devices, isDiscovering, error) | Discovery, relay toggle, rollback |
| `SettingsViewModel` | `SettingsState` (dark mode, notifications, pairingKey) | Persist/read preferences |

#### 3. Data Layer

| Component | Technology | Purpose |
|-----------|-----------|---------|
| `UdpDiscoveryService` | `DatagramSocket` (Kotlin Flow) | Broadcast discovery, collect responses |
| `RelayApiService` | OkHttp | Send relay commands to ESP32 HTTP API |
| `SettingsRepository` | DataStore Preferences | Persist app settings to disk |

---

## ESP32S3 Firmware Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  ESP32S3 Firmware                         │
│                                                          │
│  setup()                                                 │
│    │                                                     │
│    ├── setupHardware()  ──▶ Probe D0-D3 for relays       │
│    │                        Build activeRelays[]          │
│    │                                                     │
│    ├── setupWiFi()      ──▶ Connect (15s timeout)        │
│    │                        Auto-restart if failed        │
│    │                                                     │
│    ├── setupMDNS()      ──▶ Register "safelink.local"    │
│    │                                                     │
│    ├── setupOTA()       ──▶ Enable wireless updates      │
│    │                                                     │
│    ├── udp.begin(8888)  ──▶ Listen for discovery         │
│    │                                                     │
│    └── setupHTTP()      ──▶ Register API endpoints       │
│                              /api/status  (GET)           │
│                              /api/relay/toggle (POST)     │
│                                                          │
│  loop()                                                  │
│    ├── reconnectWiFiIfNeeded()  ──▶ every 5s if dropped  │
│    ├── ArduinoOTA.handle()      ──▶ check for updates    │
│    └── handleUdpDiscovery()     ──▶ check UDP packets    │
└──────────────────────────────────────────────────────────┘
```

### Relay Detection Algorithm

```
For each probe pin [D0, D1, D2, D3]:
  1. Set pin to INPUT_PULLUP
  2. Wait 20ms for voltage to settle
  3. Read pin state:
     - LOW  → Relay module detected (pulls line to GND)
               → Register relay, set pin to OUTPUT, default LOW (OFF)
     - HIGH → Nothing connected (floating high)
  
If no relays found:
  → Register D0 as fallback to guarantee app connectivity
```

---

## Security Model

```
App                              ESP32
 │                                 │
 │  "DISCOVER_SAFELINK:123456"     │
 │─────────── UDP Broadcast ──────▶│
 │                                 │  Compare with
 │                                 │  stored PAIRING_KEY
 │                                 │
 │   ✅ Key matches → respond      │
 │◀──────── UDP Response ──────────│
 │                                 │
 │   ❌ Key mismatch → ignore      │
 │         (no response)           │
```

- **Pairing Key** is stored in Android DataStore and in ESP32 firmware `PROGMEM`
- All communication is local-network-only — no data leaves your home
- No user accounts, no cloud tokens, no tracking

---

## Navigation Graph

```
                    ┌──────────┐
                    │  Home    │◀──── Start Destination
                    └────┬─────┘
                         │ tap device
                         ▼
                 ┌──────────────────┐
                 │  Device Detail   │ (deviceId param)
                 └──────────────────┘

Sidebar / Bottom Nav:
  Home ──▶ Scenes ──▶ Stats ──▶ Settings
```

---

## Data Flow — Relay Toggle

```
User taps toggle
       │
       ▼
HomeViewModel.toggleRelay()
       │
       ├── 1. Optimistic UI update (instant)
       │         applyRelayState(deviceId, relayId, newState)
       │
       └── 2. Launch coroutine
                 │
                 ▼
             RelayApiService.toggleRelay(ip, port, name, state)
                 │
                 ├── ✅ HTTP 200 → keep UI state (done)
                 │
                 └── ❌ Timeout/Error
                           │
                           ├── Roll back UI state
                           └── Show error snackbar
```
