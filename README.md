<p align="center">
  <img src="docs/icon.svg" width="120" height="120" alt="Ultra2 Remapper Icon" />
</p>

<h1 align="center">Ultra2 Remapper</h1>

<p align="center">
  <b>Remap the Orange Action Button & Bottom Button on Samsung Galaxy Watch Ultra & Ultra 2 (Wear OS)</b><br/>
  <sub> Instant Samsung Flashlight Toggle (Boost Brightness) ·  Media Controls ·  Google Assistant ·  Launch Any App</sub>
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

---

##  Features

- **Orange Action Button (Quick Button)**: Independently map **Single Press**, **Double Press**, and **Long Press**.
- **Bottom Button**: Remap **Long Press** (default short press remains *Back*).
- **Native Samsung Flashlight Toggle with Boost Brightness**:
  - Built-in `Flashlight` action (default for single press) targeting Samsung's official Wear OS Flashlight (`com.samsung.android.watch.flashlight`).
  - Automatically steps up to **Boost (maximum) brightness** (`level 12`) upon opening.
  - Pressing the Action Button again while the flashlight is open cleanly turns it off and closes it.
- **Media & System Controls**:
  - Play / Pause, Next Track, Previous Track, Volume Up / Down, Digital Assistant (Google Assistant / Gemini), or **launch any installed Wear OS app**.
- **Accessibility Setup Onboarding**:
  - Built-in setup check in the watch app with a one-tap shortcut to **Accessibility Settings** whenever the background service is unbound.
- **Wrist Orientation Lock**:
  - Respects your configured wrist/button orientation (`nosensor`) without rotating upside-down.
- **Emergency Siren Preserved**:
  - Holding the Action Button for 5–6 seconds still triggers Samsung's native emergency siren.

---

##  Download & Installation

Download the latest prebuilt APK from the [**Releases**](https://github.com/Aaronhrndz/ultra-remapper/releases/latest) page (`ultra-remapper-v0.6.0.apk`).

### One-Time Setup via Wireless ADB

1. On your watch, enable **Settings → Developer options → ADB debugging** and **Wireless debugging**, then pair and connect from your PC:
   ```bash
   adb pair <WATCH_IP>:<PAIRING_PORT>
   adb connect <WATCH_IP>:<CONNECTION_PORT>
   ```
2. Install the APK:
   ```bash
   adb install -r ultra-remapper-v0.6.0.apk
   ```
3. Route the Orange Action Button to **Ultra2 Remapper**:
   ```bash
   adb shell settings put global setting_action_button_press com.bennybar.ultraremapper/com.bennybar.ultraremapper.ActionButtonService
   ```
4. Allow background app/activity launches and exempt from battery optimization:
   ```bash
   adb shell appops set com.bennybar.ultraremapper SYSTEM_ALERT_WINDOW allow
   adb shell cmd deviceidle whitelist +com.bennybar.ultraremapper
   ```
5. Enable the **Accessibility Service** (required for Bottom Button long-press and Flashlight toggle/Boost):
   - Directly on the watch: Open **Ultra2 Remapper** → tap **Enable Accessibility** → enable **Ultra2 Remapper** under *Installed apps*.
   - Or via ADB:
     ```bash
     adb shell settings put secure enabled_accessibility_services com.bennybar.ultraremapper/com.bennybar.ultraremapper.BottomButtonService
     adb shell settings put secure accessibility_enabled 1
     ```

> **Note:** Changing the Action Button mapping inside Samsung's native Watch Settings will overwrite step 3. Also note that Samsung's firmware disables the physical Action Button while the watch is resting on the wireless charger.
>
> To restore Samsung's default Action Button behavior at any time:
> ```bash
> adb shell settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService
> ```

---

## ⚙️ How It Works Under the Hood

- **Action Button (`ActionButtonService`)**: The Galaxy Watch Ultra Action Button does not emit standard key events to apps or accessibility services. Instead, Samsung's `PhoneWindowManagerExt` starts the component stored in `Settings.Global.setting_action_button_press`, passing `ACTION_KEY_EVENT` extras (`2` = single press, `5` + `ACTION_KEY_COUNT=2` = double press, `3` = long press).
- **Bottom Button & Flashlight Control (`BottomButtonService`)**:
  - Intercepts `KEYCODE_STEM_PRIMARY` via `AccessibilityService.onKeyEvent`. Short presses trigger `GLOBAL_ACTION_BACK`; a `500 ms` hold triggers your custom mapping instead of Samsung Wallet.
  - Detects when `com.samsung.android.watch.flashlight` enters the foreground (`TYPE_WINDOW_STATE_CHANGED`) after being triggered by the `Flashlight` action and automatically steps Samsung's internal brightness controller from default (`level 10`) to **Boost mode** (`level 12`), and allows single-press toggling off via `GLOBAL_ACTION_BACK`.

---

##  Credits & Acknowledgments

This project is an active fork of [**bennybar/ultra-remapper**](https://github.com/bennybar/ultra-remapper), originally created by [**@bennybar**](https://github.com/bennybar). Huge thanks to Benny for reverse-engineering Samsung's `setting_action_button_press` service mechanism and building the original foundation of this app!

---

##  License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)** — maintaining the exact same free software license as the original upstream project. See [LICENSE](LICENSE) for full details.
