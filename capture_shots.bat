@echo off
C:\Users\aaron\.local\bin\adb.exe connect 192.168.1.223:36103
timeout /t 1 /nobreak >nul
C:\Users\aaron\.local\bin\adb.exe -s 192.168.1.223:36103 shell am start -n com.bennybar.ultraremapper/.MainActivity
timeout /t 2 /nobreak >nul
C:\Users\aaron\.local\bin\adb.exe -s 192.168.1.223:36103 shell screencap -p /sdcard/shot1.png
C:\Users\aaron\.local\bin\adb.exe -s 192.168.1.223:36103 pull /sdcard/shot1.png C:\temp\ultra-remapper\shot1.png
C:\Users\aaron\.local\bin\adb.exe -s 192.168.1.223:36103 shell input swipe 240 380 240 100 300
timeout /t 1 /nobreak >nul
C:\Users\aaron\.local\bin\adb.exe -s 192.168.1.223:36103 shell screencap -p /sdcard/shot2.png
C:\Users\aaron\.local\bin\adb.exe -s 192.168.1.223:36103 pull /sdcard/shot2.png C:\temp\ultra-remapper\shot2.png
echo DONE
