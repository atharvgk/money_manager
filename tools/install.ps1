# Installs (or updates) the app on a phone connected by USB, keeping its data.
# Usage, from the project folder:   .\tools\install.ps1            (debug APK)
#                                   .\tools\install.ps1 -Release   (faster, minified build)
param([switch]$Release)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = if ($Release) { "app\build\outputs\apk\release\app-release.apk" } else { "app\build\outputs\apk\debug\app-debug.apk" }
if (-not (Test-Path $apk)) { Write-Error "$apk not found. Build it first: .\gradlew assembleDebug"; exit 1 }
& $adb devices
& $adb install -r $apk
