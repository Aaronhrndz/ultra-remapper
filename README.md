<p align="center">
  <img src="docs/icon.svg" width="120" height="120" alt="Ultra Remapper Icon" />
</p>

<h1 align="center">Ultra Remapper</h1>

<p align="center">
  <b>Hardware Button Remapper for Samsung Galaxy Watch Ultra, Ultra 2, Watch 8 Classic & Wear OS Watches</b><br/>
  <sub>Dynamic Hardware Detection · Quick Button & Bottom Button Mapping · Samsung Flashlight Boost · Samsung Health Workouts & Favorites · Standalone OTA</sub>
</p>

<p align="center">
  <a href="https://github.com/Aaronhrndz/ultra-remapper/releases/latest">
    <img src="https://img.shields.io/github/v/release/Aaronhrndz/ultra-remapper?color=FF5500&label=latest%20release&style=flat-square" alt="Latest Release" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="License: GPL-3.0" />
  </a>
  <img src="https://img.shields.io/badge/platform-Wear%20OS%205%2B-3DDC84?style=flat-square" alt="Wear OS" />
</p>

<p align="center">
  <img src="docs/captura2.png" width="180" alt="Quick Button Mapping" />
  <img src="docs/captura4.png" width="180" alt="Action Selector" />
  <img src="docs/captura3.png" width="180" alt="Bottom Button Mapping" />
  <img src="docs/captura.png" width="180" alt="OTA Update Detection" />
  <img src="docs/captura1.png" width="180" alt="In-Watch Installer" />
</p>

---

## Features
 
- **Hardware-Aware Button Remapping**:
  - **Quick / Action Button (Center Button)**: On models equipped with a third physical key (**Galaxy Watch Ultra, Ultra 2, Watch 8 Classic**), independently remap **Single Press**, **Double Press**, and **Long Press**.
  - **Dynamic Device Detection**: On standard 2-button watches without a physical center button (e.g. Galaxy Watch 4, 5, 6, 7), the center button configuration dynamically hides to keep the UI clean, while the **Bottom (Back) Button** remains fully customizable.
  - **Bottom Button (Back Key)**: Remap **Double Press**, **Triple Press**, and **Long Press** across all supported Galaxy Watches. Rapid double and triple taps execute cleanly with zero conflict against Samsung's hardcoded 500ms long-press timer (avoiding unwanted Samsung Wallet launches), while preserving the native *Back* navigation on single press.
- **Direct In-Watch Updates (Autonomous OTA)**:
  - Automatic GitHub release detection with a live **download progress bar & percentage indicator**, and one-tap in-watch installer (`PackageInstaller` + `FileProvider`) — no phone app or ADB required for future updates.
- **Categorized Action Picker**:
  - **Smart Utilities**:
    - **Wireless Debugging (ADB)**: Direct shortcut that opens the Wireless Debugging screen and automatically toggles on ADB Wi-Fi via accessibility after a 1.2s settling delay, displaying your connection IP and port immediately on your wrist.
    - **Keep Screen Awake (Sideload / ADB)**: Keeps the screen awake and prevents Wi-Fi/CPU sleep during wireless ADB package installations, avoiding lock screen PIN re-prompts.
    - **Save Parking Spot & Navigate to Car**: Saves exact GPS coordinates with 1 press and opens Google Maps in walking navigation mode (`google.navigation:q=LAT,LON&mode=w`).
    - **Touch Sensitivity (Glove Mode)**: Toggles Samsung high-sensitivity screen mode for gloves or wet hands.
    - **Red Flashlight (Max)**: Full-screen red light at 100% brightness (tactical night vision preserving).
    - **Do Not Disturb**: Quick toggle for DND with tactile vibration feedback.
    - **Water Eject Speaker**: High-resonance acoustic wave (145 Hz -> 240 Hz sweep with envelope smoothing) at maximum hardware volume with synchronized vibration bursts to expel water droplets from the speaker grille.
    - **LTE & Mobile Data**: Toggle cellular connectivity on LTE watch variants.
  - **Samsung Functions**:
    - **Workout / Favorites (Stock)**: Triggers Samsung Health's native Quick Button hook (`ActionButtonService`), jumping straight into your pinned **Favorite Exercises** or starting your default exercise shortcut.
    - **All Exercises (Full List)**: Direct shortcut to launch Samsung Health's root workout list (`ExerciseActivity`).
    - **Route Workout Mode**: Direct shortcut to launch Samsung Health Route & GPX workout view (`shealth_route`).
    - **Flashlight (Boost)**: Opens Samsung's official Wear OS Flashlight (`com.samsung.android.watch.flashlight`), verifies active foreground window, steps up to **Boost (maximum) brightness** (`level 12`), and toggles off cleanly on repeat press. Uses dynamic `serviceInfo` event subscription so idle battery impact is **zero**.
    - **Find My Phone**: Rings your paired phone via Samsung Find or Google Find My Device.
    - **Stopwatch**: Launches Samsung Stopwatch (`com.samsung.android.watch.stopwatch`).
    - **Water Lock**: Activates Samsung's native Water Lock mode (`StWaterLockSettingActivity`).
    - **Digital Assistant**: Triggers Google Assistant / Gemini (`ACTION_VOICE_COMMAND`).
  - **Home Assistant Companion**:
    - **Home Assistant Assist (Voice)**: Launches the native voice assistant of the official Home Assistant Companion app without needing server logins on the watch.
  - **Media & Volume (with Bridged Phone & KDE Connect Support)**:
    - **Play / Pause**, **Next Track**, **Previous Track**, **Volume Up**, **Volume Down**, and **Open Media Controller**.
    - Works with both local watch audio and **bridged phone media sessions** (including **KDE Connect** controlling a tablet or PC, Spotify, YouTube, etc.) via `MediaSessionManager` + `NotificationListenerService`.
  - **Open App…**:
    - Dedicated app submenu with real circular app icons for every installed launcher app.
- **Multilingual Support**:
  - Full UI and action translations in **English**, **Español**, **Deutsch**, and **Français**.
- **Smart Contextual Diagnostics**:
  - Action Required banners (*"Acciones necesarias"*) appear only if the user's selected actions actually demand elevated permissions (e.g. Accessibility only for bottom button/flashlight boost, Secure Settings only for glove/LTE/sideload mode). Basic app launches and Samsung stock actions require zero extra permissions.

---

## Download & Installation

Download the latest prebuilt APK from the [**Releases**](https://github.com/Aaronhrndz/ultra-remapper/releases/latest) page (`ultra-remapper-v1.0.0.apk`).

### One-Time Setup via Wireless ADB

1. Enable **Settings → Developer options → ADB debugging** and **Wireless debugging** on your watch, then pair and connect:
   ```bash
   adb pair <WATCH_IP>:<PAIRING_PORT>
   adb connect <WATCH_IP>:<CONNECTION_PORT>
   ```
2. Install the APK:
   ```bash
   adb install -r ultra-remapper-v0.9.0.apk
   ```
3. Route the Orange Quick Button to **Ultra Remapper**:
   ```bash
   adb shell settings put global setting_action_button_press com.bennybar.ultraremapper/com.bennybar.ultraremapper.ActionButtonService
   ```
4. Allow background app/activity launches and exempt from battery optimization:
   ```bash
   adb shell appops set com.bennybar.ultraremapper SYSTEM_ALERT_WINDOW allow
   adb shell cmd deviceidle whitelist +com.bennybar.ultraremapper
   ```
5. Enable the **Accessibility Service** (for Bottom Button long-press and Flashlight Boost/toggle):
   ```bash
   adb shell appops set com.bennybar.ultraremapper ACCESSIBILITY Allow
   adb shell settings put secure enabled_accessibility_services com.bennybar.ultraremapper/com.bennybar.ultraremapper.BottomButtonService
   adb shell settings put secure accessibility_enabled 1
   ```
6. Enable the **Media Session Bridge** (so Media Play/Pause/Next/Prev controls bridged phone notifications like **KDE Connect**, Spotify, etc.):
   ```bash
   adb shell cmd notification allow_listener com.bennybar.ultraremapper/com.bennybar.ultraremapper.MediaControlService
   ```
7. Grant **Location & Secure Settings** (for Parking GPS, LTE toggle, and Glove Mode):
   ```bash
   adb shell pm grant com.bennybar.ultraremapper android.permission.ACCESS_FINE_LOCATION
   adb shell pm grant com.bennybar.ultraremapper android.permission.WRITE_SECURE_SETTINGS
   ```

> **Note:** Changing the Quick Button mapping inside Samsung's native Watch Settings will overwrite step 3. To restore Samsung's default Quick Button behavior at any time:
> ```bash
> adb shell settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService
> ```

---

## Credits & Acknowledgments

This project is an independent evolution of [**bennybar/ultra-remapper**](https://github.com/bennybar/ultra-remapper), originally created by [**@bennybar**](https://github.com/bennybar). Credit to Benny for reverse-engineering Samsung's `setting_action_button_press` service mechanism.

---

## License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for full details.
