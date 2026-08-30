# SafeLink — Design System

A complete guide to the visual design, color system, typography, and UI components used in the SafeLink Android app.

---

## Design Philosophy

SafeLink follows **Material Design 3** principles with a custom color palette inspired by smart home technology — clean teals, mint greens, and deep dark backgrounds that feel calm and trustworthy.

| Principle | Approach |
|-----------|----------|
| **Clarity** | High contrast text, generous spacing, clear hierarchy |
| **Responsiveness** | Instant optimistic UI updates; no waiting spinners for toggles |
| **Depth** | Layered surfaces, subtle elevation via `tonalElevation` |
| **Motion** | Sliding transitions between screens, color animations on relay state |
| **Dark-first** | Designed primarily for dark mode; light mode fully supported |

---

## Color Palette

### Primary Brand Colors

| Name | Hex | Usage |
|------|-----|-------|
| `TealAccent` | `#00BCD4` | Primary actions, active state, selected tabs, buttons |
| `MintGreen` | `#69F0AE` | Secondary accent, gradients, online indicators |

```kotlin
// app/src/main/java/com/safelink/app/ui/theme/Color.kt
val TealAccent = Color(0xFF00BCD4)
val MintGreen  = Color(0xFF69F0AE)
```

### Semantic Colors (Device Cards)

| Name | Hex | Usage |
|------|-----|-------|
| `LightOrange` | `#FFA000` | Light relay card — slider, icon tint |
| `LightOrangeBg` | `#FFF8E1` | Light relay card — background hint |
| `FanBlue` | `#2979FF` | Fan relay card — slider, icon tint |
| `FanBlueBg` | `#E3F2FD` | Fan relay card — background hint |

---

## Theme Configuration

The app uses **Material 3 Dynamic Color** with a custom dark/light scheme.

```
Dark Theme:
  Background:   #0D1117  (near-black)
  Surface:      #161B22  (card backgrounds)
  Primary:      #00BCD4  (TealAccent)
  OnPrimary:    #FFFFFF
  Outline:      #30363D

Light Theme:
  Background:   #F6F8FA
  Surface:      #FFFFFF
  Primary:      #007B8A  (darker teal)
  OnPrimary:    #FFFFFF
  Outline:      #D0D7DE
```

### Dynamic Theme Switching

Dark mode is stored in DataStore and applied at the root `SafeLinkTheme` composable:

```kotlin
// MainActivity.kt
SafeLinkTheme(darkTheme = darkModeEnabled) {
    MainScreen()
}
```

---

## Typography

All text uses **Material 3 Typography** with system defaults. Recommended Google Font for custom builds: **Inter** or **Outfit**.

| Style | Usage |
|-------|-------|
| `displayLarge` | Not used |
| `titleLarge` | Screen titles in TopAppBar |
| `titleMedium` | Card headers, section titles |
| `bodyLarge` | Primary body text |
| `bodyMedium` | Secondary descriptions |
| `bodySmall` | Subtitles, hints, timestamps |
| `labelMedium` | Badge counts, chip labels |
| `labelSmall` | Bottom nav labels, captions |

---

## Spacing System

All spacing uses multiples of **4dp** following Material 3 guidelines.

| Token | Value | Usage |
|-------|-------|-------|
| `xs` | 4dp | Icon internal padding |
| `sm` | 8dp | Compact padding |
| `md` | 16dp | Standard screen/card padding |
| `lg` | 24dp | Section spacing |
| `xl` | 32dp | Large section spacing |
| `xxl` | 48dp+ | Hero section / empty state |

---

## Component Library

### DeviceCard

Displays a single ESP32 device in the Home screen list.

```
┌─────────────────────────────────────┐
│  🔌  Living Room Controller     ●   │  ← online indicator (MintGreen)
│       192.168.1.50  |  2 relays     │
│                                     │
│  [💡 Light  ●────────────]  ON      │  ← relay row with toggle
│  [💨 Fan    ○────────────]  OFF     │
└─────────────────────────────────────┘
```

**File**: `app/src/main/java/com/safelink/app/ui/components/DeviceCard.kt`

---

### LightControllerCard

Full control card shown in Device Detail for Light relays.

```
┌──────────────────────────────────────────────┐
│  💡  Light              [ON  ●]              │
│       Smart Light                             │
│                                               │
│         ⭕ 87%         QUICK ACTIONS          │
│         ☀ Brightness  [Bright] [Dim  ]       │
│                        [Night ] [Relax]       │
└──────────────────────────────────────────────┘
```

- Circular slider built with Compose `Canvas` + `detectDragGestures`
- Slider color: `LightOrange`
- Quick action buttons use `FilledTonalButton`

---

### FanControllerCard

Full control card shown in Device Detail for Fan relays.

```
┌──────────────────────────────────────────────┐
│  💨  Fan                [OFF ○]              │
│       Smart Fan                               │
│                                               │
│         ○ 0%           QUICK ACTIONS          │
│         ⚡ Speed        [Low ] [Med ]         │
│                         [High] [Turbo]        │
└──────────────────────────────────────────────┘
```

- Circular slider: `FanBlue` when active, `outline` color when off
- Speed quick actions: Low/Med/High/Turbo

---

### GenericControllerCard

Fallback card for relays not named "Light" or "Fan".

- Shows relay name and icon dynamically assigned based on name keywords
- Simple ON/OFF toggle only — no slider

---

### SettingsToggleItem

Reusable row component in the Settings screen.

```
┌──────────────────────────────────────────────┐
│  [icon]  Title              [  ●  ]          │
│           Subtitle text                       │
└──────────────────────────────────────────────┘
```

- Uses Material 3 `Switch` with `TealAccent` thumb color
- Backed by DataStore — changes persist immediately

---

## Navigation Design

### Bottom Navigation Bar

4 tabs with icon + label. Selected state: `TealAccent` icon, pill indicator background at 15% opacity.

```
┌──────────────────────────────────────┐
│  🏠      ✨      📊      ⚙️           │
│  Home  Scenes  Stats  Settings       │
└──────────────────────────────────────┘
```

### Navigation Drawer (Sidebar)

Slides in from the left. Triggered by the hamburger icon.

```
┌──────────────────────┐
│ ╔════════════════╗   │
│ ║  Gradient      ║   │  ← TealAccent → MintGreen vertical gradient
│ ║  Header        ║   │
│ ║  SafeLink      ║   │
│ ╚════════════════╝   │
│                      │
│  🏠 Home             │  ← highlighted with teal when active
│  ✨ Scenes           │
│  📊 Statistics       │
│  ⚙️ Settings         │
│  ─────────────       │
│  ❓ Help & Support   │
│  ℹ️ About SafeLink   │
│  🐛 Send Feedback    │
│                      │
│  SafeLink v1.0.0     │  ← footer
└──────────────────────┘
```

---

## Screen Transitions

All screen transitions use `AnimatedContentTransitionScope` with a 250ms slide animation.

| Direction | Trigger |
|-----------|---------|
| Slide Left | Navigating forward (Home → Device Detail) |
| Slide Right | Navigating back (Device Detail → Home) |

```kotlin
enterTransition = { slideIntoContainer(SlideDirection.Left, tween(250)) }
exitTransition  = { slideOutOfContainer(SlideDirection.Left, tween(250)) }
```

---

## Elevation & Surfaces

| Component | Elevation |
|-----------|-----------|
| Bottom Navigation Bar | 8dp tonal elevation |
| Cards (DeviceCard, Controller Cards) | Material surface with `RoundedCornerShape(24.dp)` |
| Navigation Drawer | Material 3 ModalDrawerSheet |
| Top App Bar | 0dp (flush with background) |

---

## Iconography

All icons are from `androidx.compose.material.icons` (Material Symbols).

| Context | Icon |
|---------|------|
| Light relay | `Icons.Default.Lightbulb` |
| Fan relay | `Icons.Default.Air` |
| TV relay | `Icons.Default.Tv` |
| AC relay | `Icons.Default.AcUnit` |
| Generic relay | `Icons.Default.DeviceHub` |
| Home tab | `Icons.Default.Home` |
| Scenes tab | `Icons.Default.AutoAwesome` |
| Stats tab | `Icons.Default.BarChart` |
| Settings tab | `Icons.Default.Settings` |
| Back button | `Icons.AutoMirrored.Filled.ArrowBack` |
| Help | `Icons.AutoMirrored.Filled.Help` |

---

## Accessibility

| Feature | Implementation |
|---------|---------------|
| Content descriptions | All icons have `contentDescription` |
| Touch targets | Minimum 48dp per Material 3 spec |
| Color contrast | Tested for WCAG AA compliance in both dark and light modes |
| Screen reader | Standard Compose semantics preserved |
