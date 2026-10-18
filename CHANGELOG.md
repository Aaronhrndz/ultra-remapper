# Changelog

All notable changes to **Ultra Remapper** will be documented in this file.

## [v0.9.0] - 2026-10-18

### Added
- **Direct In-Watch APK Installer**: Download and install future updates directly on the watch without requiring a paired smartphone or ADB commands, utilizing Android's native `PackageInstaller` and `FileProvider`.
- **Samsung Health Route Workout Mode**: Direct shortcut to launch Samsung Health Route & GPX workout view (`shealth_route`).
- **High-Resonance Water Ejection**: Sound synthesized on the hardware alarm/emergency audio channel with maximum speaker volume and pulsing vibration cadence to displace water from speaker grilles.
- **Enhanced UI Sizing**: Scaled Quick Button and Bottom Button section icons by 150% (from 32 dp to 48 dp) for enhanced visual clarity on circular AMOLED screens.
- **Marquee Text**: Automated horizontal scrolling labels for actions and app names exceeding button widths.
- **Full Localization**: Complete translations across English, Español, Deutsch, and Français for all diagnostic banners (*"Acciones necesarias"*, *"Action Required"*), status prompts, and system utilities.

### Changed
- Reorganized section headers with increased top spacing (`20.dp`) for better visual hierarchy.
- Streamlined Home Assistant integration to prioritize voice assistance via official companion app without requiring server logins or redundant launch shortcuts.
- Updated Action Required diagnostic banners with high-contrast Orange pill styling matching system theme.

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
