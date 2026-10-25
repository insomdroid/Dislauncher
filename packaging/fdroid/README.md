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

## Checks still required before submission

1. Run the build and `fdroid lint`/metadata validation using the current F-Droid build-server toolchain.
2. Confirm all direct and transitive dependencies are accepted by F-Droid and their licenses are compatible.
3. Verify the provenance and license of each bundled font file and confirm first-party logo/screenshot rights.
4. Add an icon and phone screenshots to the F-Droid metadata when preparing the submission.
5. Create and test the actual metadata entry in the `fdroiddata` repository; this upstream file is only supporting documentation.
6. Keep the F-Droid version code aligned with future upstream releases and test upgrade ordering.

## Notes

The existing GitHub Actions build passing does not prove an F-Droid build will pass. The official F-Droid team makes the final inclusion decision after reviewing the source, dependencies, metadata and build result.
