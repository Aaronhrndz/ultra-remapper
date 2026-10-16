# Ultra2 Remapper

Remap the **Action button** on the Samsung Galaxy Watch Ultra 2 (Wear OS; tested on SM-L715F only) to media controls or any app, separately for single press, double press and long press.

The emergency siren (hold 5–6 s) keeps working.

## How it works

The Action button never delivers key events to apps or accessibility services. Instead, the system starts the service named in the global setting `setting_action_button_press` (by default Samsung Health's workout launcher). It passes `ACTION_KEY_EVENT` extras: `2` = single click, `5` + `ACTION_KEY_COUNT=2` = double press, `3` = long press.

Ultra2 Remapper provides that service and performs whatever you've assigned to each gesture in its settings screen.

## Setup (one-time, via adb)

1. Enable **Developer options → ADB debugging** and **Wireless debugging** on the watch, then pair and connect with `adb pair` / `adb connect`.
2. Install the APK:
   ```
   adb install ultra-remapper.apk
   ```
3. Route the Action button to Ultra2 Remapper:
   ```
   adb shell settings put global setting_action_button_press com.bennybar.ultraremapper/com.bennybar.ultraremapper.ActionButtonService
   ```
4. Allow it to open apps from the background (needed for "open app" actions):
   ```
   adb shell appops set com.bennybar.ultraremapper SYSTEM_ALERT_WINDOW allow
   ```

Open **Ultra2 Remapper** on the watch to choose an action for each gesture: open an app, play/pause, next or previous track, or volume up/down.

Changing the Action button in Samsung's settings overwrites step 3. To restore Samsung's default:
```
adb shell settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService
```

## License

GPL-3.0. See [LICENSE](LICENSE).
