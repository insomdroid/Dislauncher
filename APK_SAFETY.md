# APK Safety

Dislauncher is an independent Android app distributed through [GitHub Releases](https://github.com/insomdroid/dislauncher/releases/latest), not Google Play. Direct APK installation does not receive Google Play's app review and distribution protections.

## Download safely

- Download APKs only from the official [Dislauncher Releases page](https://github.com/insomdroid/dislauncher/releases/latest).
- Read the release notes and review the public [source code](https://github.com/insomdroid/dislauncher).
- Compare the APK's SHA-256 checksum with the matching `.sha256` release asset when available. Older releases may not include a checksum.
- Do not install APKs from unofficial mirrors or unknown people.
- Do not disable Play Protect globally to install the app. If Android warns that a file is unsafe or blocks installation, stop and investigate instead of automatically bypassing the warning.

## Signatures and updates

Android normally requires an update to be signed with the same signing key as the installed app. An APK signed with a different key may not update it. Avoid uninstalling an existing copy if you need to preserve its local data.

A public repository improves transparency, but it does not guarantee that every build is bug-free or that a downloaded APK is authentic. Install at your own discretion.

For details about app permissions and data handling, see [PRIVACY.md](PRIVACY.md).