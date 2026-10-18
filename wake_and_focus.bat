@echo off
set ADB=C:\Users\aaron\.local\bin\adb.exe
set DEV=192.168.1.223:36103

%ADB% connect %DEV%
rem Wake up display
%ADB% -s %DEV% shell input keyevent KEYCODE_WAKEUP
%ADB% -s %DEV% shell sleep 1
rem Launch MainActivity on top
%ADB% -s %DEV% shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.bennybar.ultraremapper/.MainActivity
%ADB% -s %DEV% shell sleep 2
%ADB% -s %DEV% shell dumpsys window | findstr mCurrentFocus
echo DONE
