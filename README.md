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
  <a href="https://ko-fi.com/Aaronhrndz">
    <img src="https://img.shields.io/badge/Ko--fi-Donate%20%E2%98%95-FF5E5B?style=flat-square&logo=kofi&logoColor=white" alt="Support on Ko-fi" />
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
  - **Samsung Health**:
    - **My Workouts (Stock)**: Native Quick Button hook (`ActionButtonService`), jumping straight into your pinned **Favorite Exercises** or starting your default exercise shortcut.
    - **Route Workout Mode**: Direct shortcut to launch Samsung Health Route & GPX workout view (`shealth_route`).
    - **Pick Quick Workout**: Native exercise picker selector (`ExerciseActionButtonSelectionActivity`).
  - **Samsung Functions**:
    - **Flashlight (Boost)**: Opens Samsung's official Wear OS Flashlight (`com.samsung.android.watch.flashlight`), verifies active foreground window, steps up to **Boost (maximum) brightness** (`level 12` on Ultra / supported models), and toggles off cleanly on repeat press. Uses dynamic `serviceInfo` event subscription so idle battery impact is **zero**.
    - **Find My Phone**: Rings your paired phone via Samsung Find or Google Find My Device.
    - **Stopwatch**: Launches Samsung Stopwatch (`com.samsung.android.watch.stopwatch`).
    - **Water Lock**: Activates Samsung's native Water Lock mode (`StWaterLockSettingActivity`).
    - **Digital Assistant**: Triggers Google Assistant / Gemini (`ACTION_VOICE_COMMAND`).
  - **Development (ADB)**:
    - **Wireless Debugging (ADB)**: Direct shortcut that opens the Wireless Debugging screen and automatically toggles on ADB Wi-Fi via accessibility after a 1.2s settling delay, displaying your connection IP and port immediately on your wrist.
    - **Keep Screen Awake (Sideload / ADB)**: Keeps the screen awake and prevents Wi-Fi/CPU sleep during wireless ADB package installations, avoiding lock screen PIN re-prompts.
  - **Smart Utilities**:
    - **Save Parking Spot & Navigate to Car**: Saves exact GPS coordinates with 1 press and opens Google Maps in walking navigation mode (`google.navigation:q=LAT,LON&mode=w`).
    - **Touch Sensitivity (Glove Mode)**: Toggles Samsung high-sensitivity screen mode for gloves or wet hands.
    - **Red Flashlight (Max)**: Full-screen red light at 100% brightness (tactical night vision preserving).
    - **Do Not Disturb**: Quick toggle for DND with tactile vibration feedback.
    - **Water Eject Speaker**: High-resonance acoustic wave (145 Hz -> 240 Hz sweep with envelope smoothing) at maximum hardware volume with synchronized vibration bursts to expel water droplets from the speaker grille.
    - **LTE & Mobile Data**: Toggle cellular connectivity on LTE watch variants.
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

Download the latest prebuilt APK from the [**Releases**](https://github.com/Aaronhrndz/ultra-remapper/releases/latest) page (`ultra-remapper-v1.3.24.apk`).

> [!IMPORTANT]
> **Charging State Note**: When placed on the magnetic charger dock, Wear OS enters battery charging mode and suspends accessibility and button remapping to prevent phantom touches. Button remapping resumes automatically once taken off the charger.

> [!NOTE]
> **Samsung Health Foreground Coexistence**: Button remapping remains active across the system. When Samsung Health is actively on screen, custom actions execute seamlessly alongside native exercise tracking.

> [!WARNING]
> **Upgrading from upstream versions**: If you previously had Benny's original Ultra Remapper or an older custom package installed, **uninstall it completely before installing this version** (`adb uninstall com.bennybar.ultraremapper`) to prevent package conflict or duplicated services.

### Permissions & Actions Matrix

| Category | Action / Feature | Required Permission | Elevation Method | ADB Command / Setup |
|---|---|---|---|---|
| **Hardware** | Quick / Action Button (Orange) | Global Action Hook | Watch Settings or ADB | `adb shell settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.ActionButtonService` |
| **Hardware** | Bottom Button (Double, Triple & Long Press) | `ACCESSIBILITY` | Accessibility Settings or ADB | `adb shell settings put secure enabled_accessibility_services com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.BottomButtonService`<br/>`adb shell settings put secure accessibility_enabled 1` |
| **System** | Background App / Activity Launch & Overlays | `SYSTEM_ALERT_WINDOW` | ADB AppOps *(Wear OS hides UI)* | `adb shell appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow` |
| **Media** | Bridged Phone Media Controls (Spotify, YouTube, KDE Connect) | `NotificationListenerService` | ADB *(Wear OS hides UI)* | `adb shell cmd notification allow_listener com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.MediaControlService` |
| **System** | Standalone Background Service | Battery Whitelist | ADB Whitelist | `adb shell cmd deviceidle whitelist +com.aaronhrndz.ultraremapper` |
| **Utilities** | Wireless Debugging (ADB auto-toggle) | `ACCESSIBILITY` | (Already covered by Bottom Button) | Enables Wi-Fi debugging switch automatically via accessibility node interaction |
| **Utilities** | Keep Screen Awake (Sideload / ADB) | `WAKE_LOCK` | Auto-granted | No ADB command needed |
| **Utilities** | Save Parking Spot & Navigate | `ACCESS_FINE_LOCATION` | Runtime prompt or ADB | `adb shell pm grant com.aaronhrndz.ultraremapper android.permission.ACCESS_FINE_LOCATION` |
| **Utilities** | Touch Sensitivity (Glove Mode) | `WRITE_SECURE_SETTINGS` | ADB Permission Grant | `adb shell pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS` |
| **Utilities** | LTE & Mobile Data Toggle | `WRITE_SECURE_SETTINGS` | ADB Permission Grant | `adb shell pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS` |
| **Utilities** | Do Not Disturb | `ACCESS_NOTIFICATION_POLICY` | ADB Listener or In-App prompt | Covered by notification listener / in-app grant |
| **Utilities** | Red Flashlight (Max) | None | None | Launches full-screen composable light (no extra permissions needed) |
| **Utilities** | Water Eject Speaker | `MODIFY_AUDIO_SETTINGS` | Auto-granted | Plays synthesized acoustic sweep at max stream volume |
| **Samsung** | Flashlight (Boost brightness) | `ACCESSIBILITY` | (Already covered by Bottom Button) | Monitors foreground window to step up brightness to level 12 |
| **Samsung** | Stock Workout / Favorites | None | None | Triggers native `setting_action_button_press` hook directly |
| **Samsung** | All Exercises, Route Workout | None | None | Direct package intent launch |
| **Samsung** | Find My Phone, Stopwatch, Water Lock | None | None | Direct system intent launch |
| **OTA Updates** | In-Watch Direct Installer | `REQUEST_INSTALL_PACKAGES` | Prompts on first OTA install | Uses Android `PackageInstaller` + `FileProvider` |

---

### Step-by-Step Setup via Wireless ADB

> [!NOTE]
> **Why do some permissions require ADB in Wear OS?**
> Samsung One UI Watch stripped the standard Android settings screens for **Notification Listener Access** and **Draw Over Other Apps (Overlay)**. Tapping them inside Wear OS settings normally redirects to the main settings root because the UI activity simply does not exist on the watch. Granting them via ADB is a 1-time setup.

1. Enable **Settings → Developer options → ADB debugging** and **Wireless debugging** on your watch, then pair and connect:
   ```bash
   adb pair <WATCH_IP>:<PAIRING_PORT>
   adb connect <WATCH_IP>:<CONNECTION_PORT>
   ```
2. Install the latest APK:
   ```bash
   adb install -r ultra-remapper-v1.3.23.apk
   ```
3. **One-Liner: Grant All Permissions at Once**
   Run this single command block in PowerShell or Bash to clear all 4 diagnostic banners instantly:
   ```bash
   adb shell "settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.ActionButtonService && settings put secure enabled_accessibility_services com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.BottomButtonService && settings put secure accessibility_enabled 1 && appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow && cmd notification allow_listener com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.MediaControlService && pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS && pm grant com.aaronhrndz.ultraremapper android.permission.ACCESS_FINE_LOCATION && cmd deviceidle whitelist +com.aaronhrndz.ultraremapper"
   ```

4. Or grant them individually:
   - **Route Quick / Action Button**:
     ```bash
     adb shell settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.ActionButtonService
     ```
   - **Overlay Permission** (floating UI & max flashlight):
     ```bash
     adb shell appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow
     ```
   - **Accessibility Service** (bottom button remapping):
     ```bash
     adb shell settings put secure enabled_accessibility_services com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.BottomButtonService
     adb shell settings put secure accessibility_enabled 1
     ```
   - **Media Bridge** (phone media controls & volume):
     ```bash
     adb shell cmd notification allow_listener com.aaronhrndz.ultraremapper/com.bennybar.ultraremapper.MediaControlService
     ```
   - **Secure Settings & Location** (LTE toggle, glove mode, parking GPS):
     ```bash
     adb shell pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS
     adb shell pm grant com.aaronhrndz.ultraremapper android.permission.ACCESS_FINE_LOCATION
     ```

> **Note:** Changing the Quick Button mapping inside Samsung's native Watch Settings will overwrite step 3. To restore Samsung's default Quick Button behavior at any time:
> ```bash
> adb shell settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService
> ```
---

## ☕ Support & Donations

If Ultra Remapper made your Galaxy Watch experience significantly better, consider supporting future development with a coffee:

<p align="center">
  <a href="https://ko-fi.com/Aaronhrndz">
    <img src="https://img.shields.io/badge/Support%20on-Ko--fi-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white" alt="Support on Ko-fi" />
  </a>
</p>

---

## Credits & Acknowledgments

This project is an independent evolution of [**bennybar/ultra-remapper**](https://github.com/bennybar/ultra-remapper), originally created by [**@bennybar**](https://github.com/bennybar). Credit to Benny for reverse-engineering Samsung's `setting_action_button_press` service mechanism.

---

## License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for full details.
