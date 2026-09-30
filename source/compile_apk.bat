@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

echo ===========================================
echo   MyTube r6.5 - Android Debug APK Builder
echo ===========================================
echo.

REM ============================================================
REM 1. JDK DISCOVERY
REM ============================================================
set "FOUND_JAVA_HOME="

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "FOUND_JAVA_HOME=%JAVA_HOME%"
if not defined FOUND_JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "FOUND_JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined FOUND_JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jre\bin\java.exe" set "FOUND_JAVA_HOME=C:\Program Files\Android\Android Studio\jre"
if not defined FOUND_JAVA_HOME if exist "%LOCALAPPDATA%\Google\AndroidStudio\jbr\bin\java.exe" set "FOUND_JAVA_HOME=%LOCALAPPDATA%\Google\AndroidStudio\jbr"
if not defined FOUND_JAVA_HOME if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set "FOUND_JAVA_HOME=C:\Program Files\Java\jdk-21"
if not defined FOUND_JAVA_HOME if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "FOUND_JAVA_HOME=C:\Program Files\Java\jdk-17"

if defined FOUND_JAVA_HOME (
    set "JAVA_HOME=%FOUND_JAVA_HOME%"
    set "PATH=%FOUND_JAVA_HOME%\bin;%PATH%"
    echo Found compatible JDK at: %FOUND_JAVA_HOME%
) else (
    echo ERROR: No usable JDK was found.
    echo Install Android Studio or JDK 17/21, then run this file again.
    goto BUILD_FAILED
)

echo.
echo Checking Java version...
java -version
if errorlevel 1 goto BUILD_FAILED

set "JAVA_MAJOR="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    for /f "tokens=1 delims=." %%M in ("%%~V") do set "JAVA_MAJOR=%%M"
)
if not defined JAVA_MAJOR (
    echo ERROR: Could not identify the installed Java version.
    goto BUILD_FAILED
)
if %JAVA_MAJOR% LSS 17 (
    echo ERROR: JDK 17 or newer is required.
    goto BUILD_FAILED
)

echo.

REM ============================================================
REM 2. ANDROID SDK DISCOVERY
REM ============================================================
set "FOUND_SDK="

REM First prefer explicitly configured SDK variables.
if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "FOUND_SDK=%ANDROID_SDK_ROOT%"
if not defined FOUND_SDK if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "FOUND_SDK=%ANDROID_HOME%"

REM Standard Android Studio / Android SDK installation for Windows.
if not defined FOUND_SDK if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "FOUND_SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined FOUND_SDK if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "FOUND_SDK=%USERPROFILE%\AppData\Local\Android\Sdk"
if not defined FOUND_SDK if exist "C:\Android\Sdk\platform-tools\adb.exe" set "FOUND_SDK=C:\Android\Sdk"

REM Try to derive the SDK from adb if adb is already on PATH.
if not defined FOUND_SDK (
    where adb.exe >nul 2>&1
    if not errorlevel 1 (
        for /f "delims=" %%A in ('where adb.exe') do if not defined FOUND_SDK set "FOUND_ADB=%%A"
        if defined FOUND_ADB (
            for %%A in ("%FOUND_ADB%") do set "FOUND_ADB_DIR=%%~dpA"
            for %%A in ("!FOUND_ADB_DIR!..") do set "FOUND_SDK=%%~fA"
        )
    )
)

REM Validate that the discovered directory is actually an Android SDK.
if defined FOUND_SDK if not exist "%FOUND_SDK%\platform-tools" set "FOUND_SDK="
if defined FOUND_SDK if not exist "%FOUND_SDK%\platforms" set "FOUND_SDK="
if defined FOUND_SDK if not exist "%FOUND_SDK%\platforms\android-36\android.jar" (
    echo ERROR: Android SDK Platform 36 is missing from %FOUND_SDK%.
    echo Install Android API 36 with Android Studio SDK Manager.
    goto BUILD_FAILED
)

if not defined FOUND_SDK (
    echo ERROR: Android SDK was not found.
    echo.
    echo Expected the standard SDK at:
    echo   %LOCALAPPDATA%\Android\Sdk
    echo.
    echo If your SDK is installed elsewhere, set ANDROID_SDK_ROOT to that folder.
    echo Example:
    echo   setx ANDROID_SDK_ROOT "D:\Android\Sdk"
    echo Then run this file again.
    goto BUILD_FAILED
)

REM Normalize the SDK path for Gradle's local.properties file.
set "SDK_FOR_GRADLE=%FOUND_SDK:\=/%"
set "ANDROID_SDK_ROOT=%FOUND_SDK%"
set "ANDROID_HOME=%FOUND_SDK%"

REM Generate/update local.properties automatically so Gradle never asks for sdk.dir.
echo sdk.dir=%SDK_FOR_GRADLE%> local.properties

echo Found Android SDK at: %FOUND_SDK%
echo Generated: %CD%\local.properties
echo.

REM ============================================================
REM 3. WHISPER MODEL (bundled into APK)
REM ============================================================
set "WHISPER_DIR=%CD%\app\src\main\assets\models"
set "WHISPER_MODEL=%WHISPER_DIR%\ggml-tiny-q5_1.bin"
if not exist "%WHISPER_DIR%" mkdir "%WHISPER_DIR%"
if not exist "%WHISPER_MODEL%" (
    echo Downloading multilingual Whisper Tiny model (~75 MB) once for offline Live Dub...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin' -OutFile '%WHISPER_MODEL%'"
    if errorlevel 1 (
        echo ERROR: Whisper model download failed.
        echo Internet is required once while compiling so the model can be bundled in the APK.
        goto BUILD_FAILED
    )
)
echo Whisper model ready: %WHISPER_MODEL%
echo.

REM ============================================================
REM 4. BUILD
REM ============================================================
echo Building Debug APK...
echo.

call gradlew.bat assembleDebug --stacktrace

if errorlevel 1 goto BUILD_FAILED

echo.
echo ===========================================
echo   BUILD SUCCESSFUL
echo ===========================================
echo APK Location:
echo   %CD%\app\build\outputs\apk\debug\app-debug.apk
if exist "%CD%\app\build\outputs\apk\debug\app-debug.apk" copy /Y "%CD%\app\build\outputs\apk\debug\app-debug.apk" "%CD%\MyTube-v1.2.3-Stable-Whisper-debug.apk" >nul
echo Branded copy:
echo   %CD%\MyTube-v1.2.3-Stable-Whisper-debug.apk
echo.
echo You can install the APK on an Android device now.
echo.
pause
exit /b 0

:BUILD_FAILED
echo.
echo ===========================================
echo   BUILD FAILED
echo ===========================================
echo.
echo Check the error above and run this file again after correcting it.
echo.
pause
exit /b 1
