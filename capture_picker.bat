@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%
%ADB% -s %DEV% shell input tap 240 280
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/shot_picker.png
%ADB% -s %DEV% pull /sdcard/shot_picker.png C:\temp\ultra-remapper\shot_picker.png

%ADB% -s %DEV% shell input swipe 240 380 240 80 300
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/shot_picker_scrolled.png
%ADB% -s %DEV% pull /sdcard/shot_picker_scrolled.png C:\temp\ultra-remapper\shot_picker_scrolled.png

%ADB% -s %DEV% shell input keyevent KEYCODE_BACK
%ADB% -s %DEV% shell am start -n com.bennybar.ultraremapper/.MainActivity
echo DONE
