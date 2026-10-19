# Permissions & Actions Reference

Complete matrix detailing the permissions, elevation methods, and ADB configurations required for each feature in Ultra Remapper.

| Category | Action / Feature | Required Permission | Elevation Method | ADB Command / Setup |
|---|---|---|---|---|
| **Hardware** | Quick / Action Button (Orange) | Global Action Hook | Watch Settings or ADB | `adb shell settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.ActionButtonService` |
| **Hardware** | Bottom Button (Double, Triple & Long Press) | `ACCESSIBILITY` | Accessibility Settings or ADB | `adb shell settings put secure enabled_accessibility_services com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.BottomButtonService`<br/>`adb shell settings put secure accessibility_enabled 1` |
| **System** | Background App / Activity Launch & Overlays | `SYSTEM_ALERT_WINDOW` | ADB AppOps *(Wear OS hides UI)* | `adb shell appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow` |
| **Media** | Bridged Phone Media Controls (Spotify, YouTube, KDE Connect) | `NotificationListenerService` | ADB *(Wear OS hides UI)* | `adb shell cmd notification allow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService` |
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
