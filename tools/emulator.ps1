<#
  Try Money Manager on this laptop, in an Android emulator (a virtual phone in a window). No real phone needed.

  From the project folder, in PowerShell:
      .\tools\emulator.ps1              start the virtual phone, install the latest app build, open it
      .\tools\emulator.ps1 -Seed        ...and also give it ~3 months of sample bank SMS (fills Compare/Recurring)
      .\tools\emulator.ps1 -Fresh       ...but uninstall the app first, to start again from onboarding
      .\tools\emulator.ps1 -Wipe        factory-reset the whole virtual phone (slow first boot again)
      Switches combine, e.g.  .\tools\emulator.ps1 -Fresh -Seed

  Then use .\tools\send-sms.ps1 to "receive" a new bank SMS live (see that file).
  Close the emulator window when you're done; it starts faster next time.
#>
param([switch]$Seed, [switch]$Fresh, [switch]$Wipe)

$root = Split-Path $PSScriptRoot -Parent
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$emulator = "$sdk\emulator\emulator.exe"
$avd = "money_test"
$image = "system-images;android-36;google_apis_playstore;x86_64"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

$jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
$env:ANDROID_HOME = $sdk

function Test-EmulatorRunning { return [bool](@(& $adb devices) -match "^emulator-\d+\s+device") }

# 1. Build the latest app (takes a few seconds when nothing changed).
Write-Host "Building the app..." -ForegroundColor Cyan
Push-Location $root
& .\gradlew.bat assembleDebug --quiet
$built = $LASTEXITCODE -eq 0
Pop-Location
if (-not $built -or -not (Test-Path $apk)) { Write-Error "Build failed, see the messages above."; exit 1 }

# 2. Create the virtual phone (once).
if ((& $emulator -list-avds) -notcontains $avd) {
    Write-Host "Creating the virtual phone '$avd' (one time only)..." -ForegroundColor Cyan
    "no" | & "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n $avd -k $image -d pixel_7 --force | Out-Null
}

# 3. Start it, unless it's already running.
if ($Wipe -and (Test-EmulatorRunning)) {
    Write-Host "Shutting down the running emulator to reset it..." -ForegroundColor Cyan
    & $adb emu kill | Out-Null
    Start-Sleep -Seconds 8
}
if (-not (Test-EmulatorRunning)) {
    $emuArgs = @("-avd", $avd, "-gpu", "auto", "-no-boot-anim")
    if ($Wipe) { $emuArgs += "-wipe-data" }
    Write-Host "Starting the virtual phone. The first start takes 2-4 minutes; later ones are much faster..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList $emuArgs
}
& $adb wait-for-device | Out-Null
do {
    Start-Sleep -Seconds 3
    $booted = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
} until ($booted -eq "1")
Write-Host "Virtual phone is ready." -ForegroundColor Green

# 4. Google Messages must be the default SMS app, or test SMS aren't saved to the inbox like on a real phone.
#    Right after the first boot this can take a few tries.
for ($i = 0; $i -lt 20; $i++) {
    & $adb shell cmd role add-role-holder android.app.role.SMS com.google.android.apps.messaging 0 2>$null | Out-Null
    if ((& $adb shell cmd role get-role-holders android.app.role.SMS 2>$null | Out-String) -match "messaging") { break }
    Start-Sleep -Seconds 3
}

# 5. Install (or update) the app.
if ($Fresh) {
    Write-Host "Removing the old app and its data..." -ForegroundColor Cyan
    & $adb uninstall app.moneymanager 2>$null | Out-Null
}
Write-Host "Installing $apk ..." -ForegroundColor Cyan
& $adb install -r $apk

# 6. Optional sample history.
if ($Seed) {
    Write-Host "Sending ~3 months of sample bank SMS (about a minute)..." -ForegroundColor Cyan
    & "$PSScriptRoot\send-sms.ps1" -History
}

# 7. Open the app.
& $adb shell am start -n app.moneymanager/.ui.MainActivity | Out-Null
Write-Host ""
Write-Host "Money Manager is open in the emulator window. Use the mouse like a finger." -ForegroundColor Green
Write-Host "Send a live bank SMS:   .\tools\send-sms.ps1" -ForegroundColor Green
