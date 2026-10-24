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

- Pushes to `main` trigger the test-build workflow for changes under `app/`, `gradle/`, the root Gradle files/wrapper, `Screenshot/dislauncher.png`, and the configured workflow files.
- Documentation-only changes and other screenshot changes do not trigger that workflow.
- CI runs Android lint and creates a temporary test APK artifact. It is not an official release.
- Official releases are built from version tags such as `v1.1.1`. The release workflow requires production-signing secrets, verifies the APK signature, and publishes the APK with a SHA-256 checksum.

Download releases from the [official Releases page](https://github.com/insomdroid/Dislauncher/releases/latest).
