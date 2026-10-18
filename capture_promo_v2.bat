@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%
rem Open clean to Home
%ADB% -s %DEV% shell am force-stop com.bennybar.ultraremapper
%ADB% -s %DEV% shell am start -n com.bennybar.ultraremapper/.MainActivity
%ADB% -s %DEV% shell sleep 2

rem Centered Home Gestures View (Quick button + Single/Double press visible)
%ADB% -s %DEV% shell input swipe 240 280 240 220 200
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/showcase_01_quick_button.png
%ADB% -s %DEV% pull /sdcard/showcase_01_quick_button.png C:\temp\ultra-remapper\showcase_01_quick_button.png

rem Tap double press item directly to enter picker
%ADB% -s %DEV% shell input tap 240 330
%ADB% -s %DEV% shell sleep 2
%ADB% -s %DEV% shell screencap -p /sdcard/showcase_02_picker_top.png
%ADB% -s %DEV% pull /sdcard/showcase_02_picker_top.png C:\temp\ultra-remapper\showcase_02_picker_top.png

rem Scroll slowly to Utilities section
%ADB% -s %DEV% shell input swipe 240 360 240 180 300
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/showcase_03_utilities.png
%ADB% -s %DEV% pull /sdcard/showcase_03_utilities.png C:\temp\ultra-remapper\showcase_03_utilities.png

rem Scroll down to Smart Parking / Health
%ADB% -s %DEV% shell input swipe 240 360 240 180 300
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/showcase_04_parking_health.png
%ADB% -s %DEV% pull /sdcard/showcase_04_parking_health.png C:\temp\ultra-remapper\showcase_04_parking_health.png

%ADB% -s %DEV% shell input keyevent KEYCODE_BACK
echo CAPTURED
