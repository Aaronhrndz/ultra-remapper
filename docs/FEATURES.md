# Full Feature List

Detailed breakdown of all features, capabilities, and actions supported by Ultra Remapper.

## 🎛️ Hardware-Aware Button Remapping
- **Quick / Action Button (Center Key)**:
  - Supported on models equipped with a third physical key (**Galaxy Watch Ultra, Ultra 2, Watch 8 Classic**).
  - Independently remap **Single Press**, **Double Press**, and **Long Press**.
- **Bottom Button (Back Key)**:
  - Remap **Double Press**, **Triple Press**, and **Long Press** across all Galaxy Watches (Ultra, 4, 5, 6, 7, 8).
  - Rapid double and triple taps execute cleanly with zero conflict against Samsung's hardcoded 500ms long-press timer (avoiding unwanted Samsung Wallet launches), while preserving native *Back* navigation on single press.

## 🔄 Autonomous In-Watch Updates (OTA)
- Automatic GitHub release detection.
- Live **download progress bar & percentage indicator**.
- One-tap in-watch installer (`PackageInstaller` + `FileProvider`) — no phone app or ADB required for future updates.

## 📱 Categorized Action Picker

### Samsung Health
- **My Workouts (Stock)**: Native Quick Button hook (`ActionButtonService`), jumping straight into your pinned **Favorite Exercises** or starting your default exercise shortcut.
- **Route Workout Mode**: Direct shortcut to launch Samsung Health Route & GPX workout view (`shealth_route`).
- **Pick Quick Workout**: Native exercise picker selector (`ExerciseActionButtonSelectionActivity`).

### Samsung Native Functions
- **Flashlight (Boost)**: Opens Samsung's official Wear OS Flashlight (`com.samsung.android.watch.flashlight`), verifies active foreground window, steps up to **Boost (maximum) brightness** (`level 12` on Ultra / supported models), and toggles off cleanly on repeat press. Zero idle battery impact.
- **Find My Phone**: Rings your paired phone via Samsung Find or Google Find My Device.
- **Stopwatch**: Launches Samsung Stopwatch (`com.samsung.android.watch.stopwatch`).
- **Water Lock**: Activates Samsung's native Water Lock mode (`StWaterLockSettingActivity`).
- **Digital Assistant**: Triggers Google Assistant / Gemini (`ACTION_VOICE_COMMAND`).

### Utilities & Tools
- **Wireless Debugging (ADB)**: Direct shortcut that opens the Wireless Debugging screen and automatically toggles on ADB Wi-Fi via accessibility, displaying connection IP and port immediately.
- **Keep Screen Awake (Sideload / ADB)**: Keeps the screen awake and prevents Wi-Fi/CPU sleep during wireless ADB package installations.
- **Save Parking Spot & Navigate to Car**: Saves exact GPS coordinates with 1 press and opens Google Maps in walking navigation mode.
- **Touch Sensitivity (Glove Mode)**: Toggles Samsung high-sensitivity screen mode for gloves or wet hands.
- **Red Flashlight (Max)**: Full-screen red light at 100% brightness (tactical night vision preserving).
- **Do Not Disturb**: Quick toggle for DND with tactile vibration feedback.
- **Water Eject Speaker**: High-resonance acoustic wave (145 Hz → 240 Hz sweep with envelope smoothing) at maximum hardware volume with synchronized vibration bursts to expel water droplets from the speaker grille.
- **LTE & Mobile Data**: Toggle cellular connectivity on LTE watch variants.

### Home Assistant Companion
- **Home Assistant Assist (Voice)**: Launches the native voice assistant of the official Home Assistant Companion app without needing server logins on the watch.

### Media & Volume Controls
- **Play / Pause**, **Next Track**, **Previous Track**, **Volume Up**, **Volume Down**, and **Open Media Controller**.
- Works with both local watch audio and **bridged phone media sessions** (including **KDE Connect** controlling a tablet or PC, Spotify, YouTube, etc.) via `MediaSessionManager` + `NotificationListenerService`.

### App Launcher
- **Open App…**: Dedicated app submenu with real circular app icons for every installed launcher app.

## 🌐 Multilingual & Contextual UX
- **Multilingual Support**: Full UI and action translations in **English**, **Español**, **Deutsch**, and **Français**.
- **Smart Contextual Diagnostics**: Action Required banners appear only if the user's selected actions actually demand elevated permissions. Basic app launches and Samsung stock actions require zero extra permissions.
