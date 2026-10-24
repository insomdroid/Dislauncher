# APK Safety

Download Dislauncher only from the [official GitHub Releases page](https://github.com/insomdroid/Dislauncher/releases/latest). The app is distributed as a direct APK, not through Google Play.

- Review Android's installation and security warnings; do not bypass them automatically.
- Compare the release's `.sha256` file with the APK when a checksum is provided. On Linux/macOS, run `sha256sum -c Dislauncher-v1.3.0.apk.sha256` from the folder containing both files. On Windows PowerShell, run `Get-FileHash .\Dislauncher-v1.3.0.apk -Algorithm SHA256` and compare the hash with the checksum file.
- The release workflow verifies that the APK certificate matches the configured production keystore before publishing the APK and checksum.
- Android updates normally require the same signing key. An APK signed with a different key may not update an installed copy.
- Back up important local data before uninstalling or switching builds.

A checksum confirms that a file matches the published digest, and signature verification confirms signing-key consistency. Neither proves that an app is free of defects or malware. Open source improves transparency, but does not guarantee that every build is authentic or bug-free.

For data handling and permissions, see [PRIVACY.md](PRIVACY.md).
