@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

rem Keep screen on while capturing
%ADB% -s %DEV% shell svc power stayon true

rem 1. Home screen top (Orange Quick Button + Single & Double press actions)
%ADB% -s %DEV% shell screencap -p /sdcard/promo_1_home.png
%ADB% -s %DEV% pull /sdcard/promo_1_home.png C:\temp\ultra-remapper\promo_1_home.png

rem 2. Scroll to show Bottom Button (Long Press Back button)
%ADB% -s %DEV% shell input swipe 240 380 240 140 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/promo_2_bottom_button.png
%ADB% -s %DEV% pull /sdcard/promo_2_bottom_button.png C:\temp\ultra-remapper\promo_2_bottom_button.png

rem 3. Tap card to enter Action Picker
%ADB% -s %DEV% shell input tap 240 320
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/promo_3_picker_apps.png
%ADB% -s %DEV% pull /sdcard/promo_3_picker_apps.png C:\temp\ultra-remapper\promo_3_picker_apps.png

rem 4. Scroll picker down to show Utilities (Flashlight, DND, Touch Sensitivity)
%ADB% -s %DEV% shell input swipe 240 380 240 140 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/promo_4_utilities.png
%ADB% -s %DEV% pull /sdcard/promo_4_utilities.png C:\temp\ultra-remapper\promo_4_utilities.png

rem 5. Scroll picker down more to show Smart Parking GPS, Water Ejection & Home Assistant
%ADB% -s %DEV% shell input swipe 240 380 240 140 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/promo_5_smart_actions.png
%ADB% -s %DEV% pull /sdcard/promo_5_smart_actions.png C:\temp\ultra-remapper\promo_5_smart_actions.png

rem Reset stayon
%ADB% -s %DEV% shell svc power stayon false
echo FINISHED
