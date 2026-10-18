@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%
%ADB% -s %DEV% shell input swipe 240 380 240 180 250
%ADB% -s %DEV% shell sleep 1
%ADB% -s %DEV% shell screencap -p /sdcard/reddit_parking_eject.png
%ADB% -s %DEV% pull /sdcard/reddit_parking_eject.png C:\temp\ultra-remapper\reddit_parking_eject.png
echo DONE
