# Builds the debug APK and installs it to a connected device.
#
# Why the TEMP redirect: on this Windows profile the JDK drops its AF_UNIX selector
# self-pipe socket in the profile Temp dir, where real-time AV scanning corrupts the
# transient socket file — every Gradle JVM then fails at Selector.open(). Pointing
# TEMP/TMP at a clean dir (C:\jtmp) fixes it for the whole build JVM tree, since child
# JVMs inherit the env. See BUILD.md.

$ErrorActionPreference = "Stop"

$clean = "C:\jtmp"
if (-not (Test-Path $clean)) { New-Item -ItemType Directory -Path $clean | Out-Null }
$env:TEMP = $clean
$env:TMP  = $clean

Write-Host "Building debug APK (TEMP=$clean)..." -ForegroundColor Cyan
& "$PSScriptRoot\gradlew.bat" assembleDebug
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed."; exit $LASTEXITCODE }

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
Write-Host "APK: $apk" -ForegroundColor Green

$adb = "adb"
$device = (& $adb devices) | Select-String -Pattern "\tdevice$"
if ($device) {
    Write-Host "Installing to connected device..." -ForegroundColor Cyan
    & $adb install -r $apk
} else {
    Write-Host "No device connected — skipping install. APK is ready above." -ForegroundColor Yellow
}
