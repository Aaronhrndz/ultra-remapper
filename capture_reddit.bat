@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%
rem Clear back to home
%ADB% -s %DEV% shell input keyevent KEYCODE_BACK
%ADB% -s %DEV% shell input keyevent KEYCODE_BACK
%ADB% -s %DEV% shell am start -n com.bennybar.ultraremapper/.MainActivity
%ADB% -s %DEV% shell sleep 1

rem 1. Home header + buttons view
%ADB% -s %DEV% shell input swipe 240 340 240 210 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_01_gestures.png
%ADB% -s %DEV% pull /sdcard/reddit_01_gestures.png C:\temp\ultra-remapper\reddit_01_gestures.png

rem Tap double press to open picker
%ADB% -s %DEV% shell input tap 240 330
%ADB% -s %DEV% shell sleep 1

rem 2. Action picker - scroll down to parking & media & health
%ADB% -s %DEV% shell input swipe 240 380 240 180 300
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_02_actions.png
%ADB% -s %DEV% pull /sdcard/reddit_02_actions.png C:\temp\ultra-remapper\reddit_02_actions.png

rem 3. Action picker - scroll down more to water eject & tactical tools
%ADB% -s %DEV% shell input swipe 240 380 240 160 300
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_03_utilities.png
%ADB% -s %DEV% pull /sdcard/reddit_03_utilities.png C:\temp\ultra-remapper\reddit_03_utilities.png

%ADB% -s %DEV% shell input keyevent KEYCODE_BACK
echo READY
