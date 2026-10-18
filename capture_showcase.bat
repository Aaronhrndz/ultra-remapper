@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%
rem Open app cleanly to top
%ADB% -s %DEV% shell am force-stop com.bennybar.ultraremapper
%ADB% -s %DEV% shell am start -n com.bennybar.ultraremapper/.MainActivity
%ADB% -s %DEV% shell sleep 2

rem Screen 1: Home top
%ADB% -s %DEV% shell screencap -p /sdcard/shot1_home.png
%ADB% -s %DEV% pull /sdcard/shot1_home.png C:\temp\ultra-remapper\shot1_home.png

rem Tap first button (Una pulsacion)
%ADB% -s %DEV% shell input tap 240 280
%ADB% -s %DEV% shell sleep 2

rem Screen 2: Action picker top
%ADB% -s %DEV% shell screencap -p /sdcard/shot2_picker.png
%ADB% -s %DEV% pull /sdcard/shot2_picker.png C:\temp\ultra-remapper\shot2_picker.png

rem Scroll action picker down to show smart utilities
%ADB% -s %DEV% shell input swipe 240 380 240 120 350
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/shot3_picker_utils.png
%ADB% -s %DEV% pull /sdcard/shot3_picker_utils.png C:\temp\ultra-remapper\shot3_picker_utils.png

rem Back to home
%ADB% -s %DEV% shell input keyevent KEYCODE_BACK
%ADB% -s %DEV% shell sleep 1
rem Scroll down on Home to show Bottom Button section
%ADB% -s %DEV% shell input swipe 240 380 240 100 350
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/shot4_home_bottom.png
%ADB% -s %DEV% pull /sdcard/shot4_home_bottom.png C:\temp\ultra-remapper\shot4_home_bottom.png

echo COMPLETED
