@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%
%ADB% -s %DEV% shell am force-stop com.bennybar.ultraremapper
%ADB% -s %DEV% shell am start -n com.bennybar.ultraremapper/.MainActivity
%ADB% -s %DEV% shell sleep 2

rem Scroll slightly down to center Quick Button cards nicely (Single & Double press)
%ADB% -s %DEV% shell input swipe 240 320 240 220 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/ultra_shot1_main.png
%ADB% -s %DEV% pull /sdcard/ultra_shot1_main.png C:\temp\ultra-remapper\ultra_shot1_main.png

rem Scroll more down to show Bottom Button (Long Press)
%ADB% -s %DEV% shell input swipe 240 350 240 100 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/ultra_shot2_bottom.png
%ADB% -s %DEV% pull /sdcard/ultra_shot2_bottom.png C:\temp\ultra-remapper\ultra_shot2_bottom.png

rem Tap the button card to enter picker
%ADB% -s %DEV% shell input tap 240 340
%ADB% -s %DEV% shell sleep 2
%ADB% -s %DEV% shell screencap -p /sdcard/ultra_shot3_picker.png
%ADB% -s %DEV% pull /sdcard/ultra_shot3_picker.png C:\temp\ultra-remapper\ultra_shot3_picker.png

rem Scroll picker down to show smart utilities (Water eject, Sideload, HA, GPS car)
%ADB% -s %DEV% shell input swipe 240 350 240 100 300
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/ultra_shot4_utilities.png
%ADB% -s %DEV% pull /sdcard/ultra_shot4_utilities.png C:\temp\ultra-remapper\ultra_shot4_utilities.png

echo CAPTURE_COMPLETE
