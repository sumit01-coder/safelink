<div align="center">

# 🔗 SafeLink

### Smart Home Relay Controller — Android + ESP32S3

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](/)
[![Platform](https://img.shields.io/badge/platform-Android-blue)](/)
[![Hardware](https://img.shields.io/badge/hardware-Xiao%20ESP32S3-orange)](/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9-purple)](/)
[![PlatformIO](https://img.shields.io/badge/firmware-PlatformIO-orange)](/)

SafeLink is an open-source smart home relay controller. It pairs an **Android app** with a **Seeed Studio Xiao ESP32S3** over your local WiFi network to control relays (lights, fans, appliances) in real time — with zero cloud dependency.

</div>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🔍 **Auto Discovery** | App scans the local network via UDP broadcast to find ESP32 devices automatically |
| 🔐 **Secure Pairing** | Unique Pairing Key prevents accidental or unauthorized connections |
| ⚡ **Auto Relay Detection** | ESP32 probes its GPIO pins at boot and dynamically reports connected relays |
| 📱 **Dynamic UI** | App builds device control cards based on what relays are physically connected |
| 🌙 **Dark Mode** | Full dark/light theme with instant switching, persisted across restarts |
| 🔄 **OTA Updates** | Update ESP32 firmware wirelessly without re-plugging USB |
| 🏠 **mDNS** | Access your device at `http://safelink.local` on the local network |
| 📴 **Offline-First** | No internet or cloud server required — everything runs on your local network |

---

## 📂 Project Structure

```
📁 New folder/
├── 📁 app/                          # Android Application (Jetpack Compose)
│   └── src/main/java/com/safelink/app/
│       ├── 📁 data/
│       │   ├── discovery/           # UDP device discovery
│       │   ├── model/               # Data models (SafeLinkDevice, Relay)
│       │   ├── network/             # HTTP relay control (RelayApiService)
│       │   └── repository/          # DataStore settings persistence
│       └── 📁 ui/
│           ├── home/                # Home screen + ViewModel
│           ├── device/              # Device Detail screen (circular sliders)
│           ├── scenes/              # Scenes screen
│           ├── stats/               # Stats screen
│           ├── settings/            # Settings screen + ViewModel
│           ├── navigation/          # NavGraph + Screen routes
│           ├── components/          # Shared UI components
│           └── theme/               # Material 3 theme, colors, typography
│
├── 📁 firmware/
│   └── xiao_esp32s3/
│       ├── platformio.ini           # PlatformIO project config
│       └── src/
│           └── main.cpp             # Full ESP32S3 firmware
│
├── 📄 README.md                     # This file
├── 📄 LICENSE                       # MIT License
├── 📄 ARCHITECTURE.md               # System architecture deep-dive
├── 📄 HOW_IT_WORKS.md               # End-to-end flow explanation
├── 📄 MODEL.md                      # Data model reference
└── 📄 DESIGN.md                     # UI/UX design system
```

---

## 🚀 Quick Start

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Android Studio | Hedgehog+ | Build & deploy the Android app |
| PlatformIO | Latest | Build & flash the ESP32 firmware |
| Python | 3.x | Required by PlatformIO |
| Seeed Studio Xiao ESP32S3 | Any | Target hardware |
| Relay Module(s) | 1-4 channel | Connected to GPIO pins |

---

### 1. Flash the ESP32 Firmware

```bash
# 1. Open the firmware folder in VS Code with PlatformIO extension
cd "firmware/xiao_esp32s3"

# 2. Edit src/main.cpp — set your WiFi credentials and Pairing Key
nano src/main.cpp
# WIFI_SSID   = "YourNetworkName"
# WIFI_PASS   = "YourPassword"
# PAIRING_KEY = "your-unique-key"    ← remember this!

# 3. Build and flash
pio run --target upload

# 4. Monitor serial output
pio device monitor
```

---

### 2. Build & Install the Android App

```bash
# From the project root
./gradlew assembleDebug

# Install to connected phone
./gradlew installDebug
```

Or open the project in **Android Studio** and click **Run ▶**.

---

### 3. Connect App to Device

1. Open the SafeLink app on your Android phone
2. Go to **Settings → Pairing Key** and enter the same key you set in the firmware
3. Go to **Home** and tap the **Scan Network** button (or the **+** icon in the top bar)
4. Your ESP32 device will appear within ~3 seconds

> **Note:** Both your phone and the ESP32 must be on the **same WiFi network**.

---

## 🔌 Wiring Guide

Connect relay modules to the Xiao ESP32S3 GPIO pins:

| Relay | Pin | Notes |
|-------|-----|-------|
| Relay 1 | D0 | Default probe pin |
| Relay 2 | D1 | |
| Relay 3 | D2 | |
| Relay 4 | D3 | |

> The firmware uses `INPUT_PULLUP` detection at boot. Relay modules with optocoupler inputs will pull the pin LOW when connected, allowing automatic detection.

---

## 📡 Network Communication

| Protocol | Port | Direction | Purpose |
|----------|------|-----------|---------|
| UDP Broadcast | 8888 | App → ESP32 | Device discovery |
| UDP Unicast | 8888 | ESP32 → App | Discovery response |
| HTTP POST | 80 | App → ESP32 | Toggle relay |
| HTTP GET | 80 | App → ESP32 | Fetch device status |

---

## 🛠 Tech Stack

### Android
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Navigation**: Navigation Compose
- **Persistence**: DataStore Preferences
- **Networking**: OkHttp
- **Serialization**: kotlinx.serialization

### Firmware
- **Platform**: Arduino framework on ESP32S3
- **Build System**: PlatformIO
- **HTTP Server**: ESPAsyncWebServer
- **JSON**: ArduinoJson v6
- **Networking**: WiFiUDP, ESPmDNS, ArduinoOTA

---

## 📄 Documentation

| File | Description |
|------|-------------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Full system architecture with diagrams |
| [HOW_IT_WORKS.md](HOW_IT_WORKS.md) | Step-by-step communication flows |
| [MODEL.md](MODEL.md) | All data models and JSON schemas |
| [DESIGN.md](DESIGN.md) | UI design system, colors, and typography |

---

## 📜 License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.
