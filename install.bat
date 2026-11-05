@echo off
rem Build Workout Log and install it on the phone plugged in by USB.
rem Double-click this file, or run it from PowerShell / Command Prompt.
cd /d "%~dp0"
rem Toolchain lives in D:\Users\<your Windows username>\Android by default (same as Slam Log).
rem To use another location, create install.local.bat (not committed) with: set ANDROID_DIR=C:\somewhere
set ANDROID_DIR=D:\Users\%USERNAME%\Android
if exist "%~dp0install.local.bat" call "%~dp0install.local.bat"
set JAVA_HOME=%ANDROID_DIR%\jdk-17
set ADB=%ANDROID_DIR%\sdk\platform-tools\adb.exe

echo.
echo === Building Workout Log (the first build takes a few minutes) ===
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the red error lines above and paste them to Claude.
  pause
  exit /b 1
)

echo.
echo === Looking for your phone ===
"%ADB%" devices
echo.
echo === Installing on the phone ===
"%ADB%" install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
  echo.
  echo INSTALL FAILED. Check the phone is plugged in, unlocked, and that you tapped Allow
  echo on "Allow USB debugging?". The built app is here if you'd rather copy it over:
  echo   %~dp0app\build\outputs\apk\debug\app-debug.apk
  pause
  exit /b 1
)

echo.
echo Done. Workout Log is in your phone's app drawer.
pause
