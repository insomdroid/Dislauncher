# F-Droid packaging preparation

This directory records the proposed packaging configuration and outstanding checks for submitting Dislauncher to the F-Droid main repository. It is not an official F-Droid approval or a file that F-Droid automatically consumes.

## Current source release

- Application ID: `com.insomdroid.dislauncher`
- Current release tag: `v1.3.1`
- Version name: `1.3.1`
- Version code: `103019999`
- Source: https://github.com/insomdroid/Dislauncher
- License: MIT; bundled fonts are documented separately under SIL OFL 1.1.

## Deterministic version values

The app Gradle build accepts these project properties for a package recipe:

- `fdroidVersionName`
- `fdroidVersionCode`

They must be provided together. Example for the current source release:

```sh
./gradlew -PfdroidVersionName=1.3.1 -PfdroidVersionCode=103019999 assembleRelease
```

This avoids relying on GitHub Actions environment variables or release-tag detection when a third-party build service builds a fixed source commit. Normal development and tagged release versioning remains unchanged when these properties are absent.

## Dependency and asset review

The first-pass direct dependency review found no advertising, analytics or Firebase/Google Play Services SDK declarations. Licenses checked from upstream project pages include:

- `com.kizitonwose.calendar:compose:2.9.1` — MIT.
- `io.noties.markwon:core:4.6.2` — Apache-2.0.
- AndroidX and Jetpack Compose dependencies — Apache-2.0 family licenses.
- `org.json:json:20260814` is test-only; Maven Central currently reports this release as public domain. Older releases used the incompatible “Good, not Evil” JSON license, so do not downgrade this test dependency to an older release.
- JUnit and Robolectric are test-only dependencies and should be checked in the actual F-Droid dependency/build scan.

The repository contains `gradle/wrapper/gradle-wrapper.jar`. It is build tooling, not an application runtime dependency, but the F-Droid recipe may need to remove it from the build checkout with `scandelete` if the scanner reports it. Do not remove the wrapper from the upstream project solely for this reason.

The font notice documents the five bundled families under SIL OFL 1.1. The actual font binaries have not yet been independently compared with upstream release artifacts. Logo and screenshot files appear to be project assets, but their provenance/license is not explicitly documented separately; confirm this before submitting.

## Checks still required before submission

1. Run the build and `fdroid lint`/metadata validation using the current F-Droid build-server toolchain.
2. Complete a dependency-tree scan for all transitives and verify the resolved artifacts/licenses in the F-Droid build environment.
3. Verify the provenance and license of each bundled font file and confirm first-party logo/screenshot rights.
4. Add an icon and phone screenshots to the F-Droid metadata when preparing the submission.
5. Create and test the actual metadata entry in the `fdroiddata` repository; this upstream file is only supporting documentation.
6. Keep the F-Droid version code aligned with future upstream releases and test upgrade ordering.

## Notes

The existing GitHub Actions build passing does not prove an F-Droid build will pass. The official F-Droid team makes the final inclusion decision after reviewing the source, dependencies, metadata and build result.
