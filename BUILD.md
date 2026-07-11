# Building Phoenix

## Prerequisites
- **JDK 17** active (`java -version` should report 17). If not:
  `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17..."`
- **Android SDK** installed; `local.properties` must point `sdk.dir` at it.
- **Android SDK Platform 35** (compileSdk/targetSdk = 35) installed via the SDK Manager.

## The Gradle wrapper jar
`gradle/wrapper/gradle-wrapper.jar` (Gradle 8.13) is present and `./gradlew.bat` works.
If it ever goes missing, regenerate it by opening the project in **Android Studio**, or with
a system Gradle: `gradle wrapper --gradle-version 8.13`.

## Verified
`assembleDebug` has been run against this source and **succeeds** (Gradle 8.13, JDK 17,
compileSdk 35) — `app-debug.apk` (~18 MB) is produced. Only benign warnings remain
(deprecated `CommandButton.Builder`/`setIconResId`, and a no-op UnstableApi opt-in note).

## Build

```powershell
# From a normal PowerShell terminal (not through an agent — Gradle needs a real JVM):
.\build-and-install.ps1
```

or manually:

```powershell
.\gradlew.bat assembleDebug          # APK -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat installDebug           # build + install to a connected phone
```

## The `TEMP=C:\jtmp` workaround (important on this machine)
On this Windows profile the JDK drops its AF_UNIX selector self-pipe socket into the
profile Temp dir, where real-time AV scanning corrupts the transient socket file — every
Gradle JVM then fails at `Selector.open()`. The fix is to run the build with `TEMP`/`TMP`
pointed at a clean directory:

```powershell
if (-not (Test-Path C:\jtmp)) { New-Item -ItemType Directory C:\jtmp | Out-Null }
$env:TEMP = "C:\jtmp"; $env:TMP = "C:\jtmp"
.\gradlew.bat assembleDebug
```

`build-and-install.ps1` already does this. The env var propagates to every child JVM the
build spawns (Gradle daemon, Kotlin compiler daemon, workers).

## After install
Grant the app the **Audio** permission (and **Images** for the car artwork slideshow) when
prompted, or in Settings → Apps → Phoenix → Permissions. Only files Android's MediaStore has
indexed are visible; use the **Rescan** button after copying new music, and note a `.nomedia`
file hides a folder from MediaStore.
