@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%

rem 1. Capture Bottom button section (Shot 1)
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_bottom_button.png
%ADB% -s %DEV% pull /sdcard/reddit_bottom_button.png C:\temp\ultra-remapper\reddit_bottom_button.png

rem 2. Scroll up to show Quick Button gestures (Shot 2)
%ADB% -s %DEV% shell input swipe 240 180 240 380 200
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_quick_button.png
%ADB% -s %DEV% pull /sdcard/reddit_quick_button.png C:\temp\ultra-remapper\reddit_quick_button.png

rem 3. Tap "Una pulsacion" to enter Action Picker (Shot 3)
%ADB% -s %DEV% shell input tap 240 280
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell input swipe 240 380 240 240 200
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_utilities.png
%ADB% -s %DEV% pull /sdcard/reddit_utilities.png C:\temp\ultra-remapper\reddit_utilities.png

rem 4. Scroll further down in Picker to show Smart Parking & Media actions (Shot 4)
%ADB% -s %DEV% shell input swipe 240 380 240 140 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_smart_actions.png
%ADB% -s %DEV% pull /sdcard/reddit_smart_actions.png C:\temp\ultra-remapper\reddit_smart_actions.png

%ADB% -s %DEV% shell input keyevent KEYCODE_BACK
echo CAPTURED_ALL
