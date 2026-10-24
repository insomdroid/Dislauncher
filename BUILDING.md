# Building Dislauncher

Dislauncher uses Kotlin, Jetpack Compose, Material 3 and Android SDK 36. Minimum supported version: Android 8.0 (API 26).

## Requirements

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Network access for the first Gradle build

## Build a debug APK

From the repository root:

```sh
./gradlew assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. On Windows, use `gradlew.bat assembleDebug`.

## GitHub Actions and releases

- Changes to app source, tests, Gradle files, release documentation, screenshots, or the configured workflows trigger the test-build workflow.
- CI runs `testReleaseUnitTest`, Android lint, and a temporary debug-signed test APK build. It does not require production-signing secrets and is not an official release.
- Official releases are built from semantic-version tags such as `v1.3.0`.
- The release workflow keeps production-signing secrets in the build job only, verifies the APK signature against the configured keystore certificate, and creates a SHA-256 checksum.
- Publishing runs in a separate job with release-write permission and uploads only the APK and checksum that passed the build/signature checks.

Download releases from the [official Releases page](https://github.com/insomdroid/Dislauncher/releases/latest).
