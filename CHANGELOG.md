# Changelog

All notable changes to **Ultra Remapper** will be documented in this file.

## [v1.0.0] - 2026-10-18

### Added
- **Major Release v1.0.0 Milestone**: Stable production architecture.
- **Contextual Permission Banners**: Action Required diagnostic banners now show only when the user's currently configured actions actually require that specific permission (e.g. Accessibility only for bottom button/flashlight boost, Secure Settings only for glove/LTE/ADB mode, Media Bridge only for media actions).
- **Direct In-Watch APK Installer**: Autonomous OTA updates directly on Wear OS via `PackageInstaller` and `FileProvider`.
- **Samsung Health Route Workout Mode**: Direct shortcut to launch Samsung Health Route & GPX workout view (`shealth_route`).
- **High-Resonance Water Ejection**: Speaker clearing acoustic sine wave on hardware emergency/alarm channel.
- **Enhanced UI Sizing**: Scaled Quick Button and Bottom Button section icons by 150% (48 dp) and marquee scrolling on long labels.
- **Full Localization**: English, Español, Deutsch, and Français.

### Changed
- Streamlined Home Assistant integration to official Companion Voice Assist.
- Reorganized section spacing and high-contrast orange theme styling.

---

## [v0.9.0] - 2026-10-18

---

## [v0.8.0] - 2026-10-17

### Added
- **Smart Utilities Section**:
  - Save Parking Spot (GPS coordinates fix).
  - Navigate to Car (Google Maps walking mode).
  - Sideload / Keep-Awake mode (prevents screen lock and WiFi timeout during ADB transfers).
  - Touch Sensitivity / Glove Mode toggle.
  - Red Flashlight (100% full screen red night vision).
  - Do Not Disturb toggle with haptic feedback.
  - LTE Cellular & Mobile Data quick toggle.
- **Home Assistant Companion Integration**: Home Assistant Assist voice assistant launch.
- **Dynamic Diagnostics**: Live diagnostic warnings for Accessibility, Quick Button route, Overlay permission, and Secure Settings.
- **GitHub Update Checker**: Detection of new GitHub releases on app startup.

---

## [v0.7.0] - 2026-10-16

### Added
- Modern Material 3 / One UI Watch interface with categorized action picker.
- Samsung native flashlight boost to maximum brightness (`level 12`) on repeat press.
- Samsung Health Workout, Exercise list, Stopwatch, and Water Lock integration.
- Media controls with bridged notification support (including KDE Connect).
- Bottom button long-press remapping via Accessibility Service.
