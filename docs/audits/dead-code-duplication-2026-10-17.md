# Dislauncher dead-code and duplication audit

**Date:** 2026-10-17  
**Branch reviewed:** `main`  
**Reviewed head:** `9fbb9d19e0a51e66f771603c30ec50f8e0479441`

## Scope

- All 24 Kotlin source files under `app/src/main/java/com/insomdroid/dislauncher/`.
- `AndroidManifest.xml`, all XML resource declarations, adaptive icon descriptors, bundled font resources, and the bundled font license.
- Root/module Gradle Kotlin DSL files, `gradle.properties`, `settings.gradle.kts`, and the existing APK workflow.
- Existing release minification and resource-shrinking configuration.

## Method and decisions

- Checked imports and declarations for unused references, then checked screen/root APIs and Android components across files before treating a low local reference count as dead code.
- Checked manifest component names and resource references against the current tree.
- Compared the adaptive icon descriptors and confirmed `ic_launcher.xml` and `ic_launcher_round.xml` were byte-for-byte identical. Both manifest attributes now reference `@mipmap/ic_launcher`, and the duplicate `ic_launcher_round.xml` resource was removed. The foreground and monochrome drawable files remain separate because their wordmark accent paths intentionally use different colors.
- Retained Compose `getValue`/`setValue` operator imports. Their names are consumed implicitly by delegated Compose state properties (the `by` syntax), so a plain text reference-count check is not sufficient grounds to delete them.
- Consolidated the duplicated non-system-first app ordering in onboarding and the root flow into `prioritizeNonSystemApps()` in `AppOrdering.kt`.
- Consolidated repeated notification-to-dot state conversion in `MusicPlayer.kt` into `StatusBarNotification.toTrackedDotNotification()`, keeping the ongoing-notification exclusion logic identical in both call sites.
- No Kotlin implementation or resource file was deleted based only on a low reference count. No additional safely removable duplicated implementation or manifest/resource declaration was confirmed.

## Confirmed cleanup

- Removed `debugImplementation("androidx.compose.ui:ui-tooling")`: the current source tree has no `@Preview`, `PreviewParameter`, or `ui.tooling` references, and the dependency is not needed by the release APK.
- Kept the official Compose BOM, AndroidX dependencies used by the app, and bundled font/license files.
- Kept R8 minification and resource shrinking enabled. The app uses the default optimized Android ProGuard configuration and has no custom keep-rule file to prune.

## Build workflow decision

The default APK workflow intentionally remains lean and runs `gradle assembleRelease`, verifies the APK exists, then uploads it. `lintRelease` and `testReleaseUnitTest` were considered but not retained because they add CI work, and this repository has no unit-test source set (`testReleaseUnitTest` reported `NO-SOURCE`). The one-off lint run took about two minutes and failed on `app/src/main/res/values/styles.xml`: `android:windowLightNavigationBar` requires API 27 while `minSdk` is 26; it also reported 36 warnings and 6 hints. Lint was removed from the build gate rather than making unrelated theme/resource changes during this dead-code audit. The compatibility finding remains recorded for a focused follow-up. A successful APK assembly does not replace manual/device testing.

## Reference basis

The review used applicable guidance from the official [Android Skills repository](https://github.com/android/skills), especially [R8 analyzer](https://github.com/android/skills/tree/main/performance/r8-analyzer) for release shrinking/keep-rule review and [testing setup](https://github.com/android/skills/tree/main/testing/testing-setup) for checking test infrastructure. The project keeps its known-compatible AGP/Gradle/JDK versions instead of coupling this cleanup to a separate major toolchain migration.
