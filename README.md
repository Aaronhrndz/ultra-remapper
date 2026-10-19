<p align="center">
  <img src="docs/icon.svg" width="120" height="120" alt="Ultra Remapper Icon" />
</p>

<h1 align="center">Ultra Remapper</h1>

<p align="center">
  <b>Hardware Button Remapper for Samsung Galaxy Watch Ultra, Ultra 2, Watch 8 Classic & Wear OS Watches</b><br/>
  <sub>Dynamic Hardware Detection Â· Quick Button & Bottom Button Mapping Â· Samsung Flashlight Boost Â· Samsung Health Workouts & Favorites Â· Standalone OTA</sub>
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

## Capabilities Overview

- **Physical Button Remapping**: Independent single, double, and long press customization for the **Quick / Center Button** (Galaxy Watch Ultra, Watch 8 Classic) and **Bottom (Back) Button** across all Wear OS Samsung watches.
- **Samsung Health & System Actions**: Instant shortcuts to pinned workouts, official Flashlight (with automatic Boost to max brightness), Stopwatch, Water Lock, and Digital Assistant.
- **Smart Utilities**: One-click Wireless ADB toggle, GPS parking saver & walking navigation, water eject speaker sweep, Glove Mode, LTE toggle, and tactical red flashlight.
- **Phone Media Controls**: Wrist-based media playback and volume control bridging local and phone sessions (Spotify, YouTube, KDE Connect).
- **Autonomous In-Watch OTA**: Background release checks with in-watch installer and live progress bar — no PC or phone app required for updates.
- **Smart Diagnostics**: Zero unnecessary banners; warning dialogs only appear when a selected action actually requires elevated permissions.

> [!TIP]
> For the complete breakdown of every action and tool, explore the [**Full Feature List**](docs/FEATURES.md).

---

## Download & Installation

Download the latest prebuilt APK from the [**Releases**](https://github.com/Aaronhrndz/ultra-remapper/releases/latest) page (`ultra-remapper-vX.X.X.apk`).

> [!IMPORTANT]
> **Charging State Note**: When placed on the magnetic charger dock, Wear OS enters battery charging mode and suspends accessibility and button remapping to prevent phantom touches. Button remapping resumes automatically once taken off the charger.

> [!NOTE]
> **Samsung Health Foreground Coexistence**: Button remapping remains active across the system. When Samsung Health is actively on screen, custom actions execute seamlessly alongside native exercise tracking.

> [!WARNING]
> **Upgrading from older versions**: Starting in **v1.3.22**, the package name changed from `com.bennybar.ultraremapper` to `com.aaronhrndz.ultraremapper`. If you previously had **v1.3.21 or older** installed, **uninstall it completely before installing this version** (`adb uninstall com.bennybar.ultraremapper`) to prevent duplicate apps or permission conflicts.

> [!TIP]
> For the complete breakdown of which features need elevated permissions, check the [**Permissions & Actions Reference**](docs/PERMISSIONS.md).

---

### ⚡ Quick Setup via Wireless ADB

1. Enable **Settings → Developer options → ADB debugging** and **Wireless debugging** on your watch, then pair and connect:
   ```bash
   adb pair <WATCH_IP>:<PAIRING_PORT>
   adb connect <WATCH_IP>:<CONNECTION_PORT>
   ```
2. Install the downloaded APK:
   ```bash
   adb install -r ultra-remapper-*.apk
   ```

> [!TIP]
> ### 🚀 Recommended: All-In-One Megacommand
> Run this single command block in your terminal (PowerShell, CMD or Bash) to **automatically grant all 4 elevated permissions and configure hardware buttons in one second**:
>
> ```bash
> adb shell "settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.ActionButtonService && settings put secure enabled_accessibility_services com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.BottomButtonService && settings put secure accessibility_enabled 1 && appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow && cmd notification allow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService && pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS && pm grant com.aaronhrndz.ultraremapper android.permission.ACCESS_FINE_LOCATION && cmd deviceidle whitelist +com.aaronhrndz.ultraremapper"
> ```
> *This automatically enroutes the Action Button, enables Accessibility for the Back key, unlocks Overlays and Media Listener, and grants Secure Settings with zero manual steps.*

<details>
<summary><b>🔧 Advanced: Grant permissions individually (Step-by-Step)</b></summary>

If you prefer to grant permissions manually one by one instead of using the megacommand:

- **1. Route Quick / Action Button**:
  ```bash
  adb shell settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.ActionButtonService
  ```
- **2. Enable Accessibility Service** (Bottom / Back button remapping):
  ```bash
  adb shell settings put secure enabled_accessibility_services com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.BottomButtonService
  adb shell settings put secure accessibility_enabled 1
  ```
- **3. Allow Overlay Permission** (floating UI & full-screen red flashlight):
  ```bash
  adb shell appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow
  ```
- **4. Enable Media Bridge** (phone media controls & volume sync):
  ```bash
  adb shell cmd notification allow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService
  ```
- **5. Grant Secure Settings & GPS Location** (LTE toggle, glove mode, parking GPS):
  ```bash
  adb shell pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS
  adb shell pm grant com.aaronhrndz.ultraremapper android.permission.ACCESS_FINE_LOCATION
  ```
- **6. Battery Whitelist** (prevent service sleep):
  ```bash
  adb shell cmd deviceidle whitelist +com.aaronhrndz.ultraremapper
  ```

> **Note:** Changing the Quick Button mapping inside Samsung's native Watch Settings will overwrite the Action Button routing. To restore Samsung's default Quick Button behavior at any time:
> ```bash
> adb shell settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService
> ```

</details>
---

## â˜• Support & Donations

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
