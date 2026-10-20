# Dislauncher dead-code and duplication audit

**Original review date:** 2026-10-17  
**Follow-up:** 2026-10-20  
**Branch:** `main`

## Original review and decisions

The original review covered Kotlin source files, manifest and XML resources, adaptive icon descriptors, bundled font/license files, Gradle configuration, and the APK workflow.

- Checked imports and declarations for unused references, then checked screen/root APIs and Android components across files before treating a low local reference count as dead code.
- Checked manifest component names and resource references against the source tree.
- Retained Compose `getValue`/`setValue` operator imports because delegated state uses them implicitly.
- Consolidated non-system-first app ordering in `AppOrdering.kt` and notification-to-dot conversion in `MusicPlayer.kt`.
- Removed the unused `androidx.compose.ui:ui-tooling` debug dependency after confirming there were no preview/tooling references.
- Kept R8 minification and resource shrinking enabled.

## Follow-up changes for public readiness

- Removed `android:windowLightNavigationBar` from base and night theme resources. The attribute triggered an API-level lint error for the project's minimum supported Android API (26).
- Added the official Gradle 8.13 Wrapper scripts, JAR and pinned distribution URL so a fresh clone can build without a globally installed Gradle.
- Updated CI to use the wrapper, run `lintRelease` before assembling, and avoid production signing secrets for test artifacts.
- Updated the release workflow to check out the exact requested tag, validate semantic-version tags, verify the production signing certificate, and publish an APK SHA-256 checksum.
- Corrected build path filters so changing `Screenshot/dislauncher.png` (the icon source used during resource generation) triggers a build, while documentation-only and gallery screenshot-only changes do not.
- Removed the temporary cleanup workflow because it iterated over all workflow runs and could delete release runs as well as build runs.
- Curated the public screenshot gallery into descriptive paths under `screenshots/`; retained `Screenshot/dislauncher.png` because the Gradle icon-generation task uses it.

The public-readiness CI workflow now runs Android lint and release-variant assembly. A successful workflow is still not a substitute for testing the installed APK on real devices, particularly optional permission flows, wallpaper targets, notification access and Device Administrator behaviour.

## Remaining decisions

- Added the MIT License and updated README licensing guidance. Confirm that the copyright holder has authority to license all project-owned code and assets before publishing.
- Updated the README Ko-fi link to the owner-provided profile: https://ko-fi.com/insomdroid.
- Full Git history secret scanning remains incomplete through the available GitHub connector; do not treat current-branch code search as a complete history scan. Run a local history-aware scanner (for example, gitleaks on a full clone) before switching repository visibility to public. A manual end-to-end device test is also still recommended.
