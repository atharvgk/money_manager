# Sets JAVA_HOME (JDK 17), ANDROID_HOME and PATH (adb) for the current PowerShell window only.
# Usage, from the project folder:   . .\tools\env.ps1      (note the leading dot and space)
$jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName } else { Write-Warning "JDK 17 not found in $env:USERPROFILE\.jdks" }
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:JAVA_HOME\bin;$env:PATH"
Write-Host "JAVA_HOME    = $env:JAVA_HOME"
Write-Host "ANDROID_HOME = $env:ANDROID_HOME"
Write-Host "adb          = $((Get-Command adb -ErrorAction SilentlyContinue).Source)"
