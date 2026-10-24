# Privacy and Permissions

**Last reviewed:** 24 October 2026

Dislauncher is an independent Android launcher. This note describes data handling in the reviewed source; Android, device manufacturers and third-party services have their own policies.

## At a glance

- No account or sign-in system.
- No advertising or analytics SDK found in the reviewed source.
- Launcher preferences, notes, reminders and selected custom fonts are stored locally.
- Weather is optional. When enabled with location access, approximate coordinates are sent over HTTPS to Open-Meteo; the Android Geocoder is also given the coordinates to resolve a locality name.
- Calendar, notifications, notification access and Device Administrator are optional features.

## Data and network use

Most settings and personal content remain in app-private storage or Android preferences and are not uploaded to a Dislauncher-operated server in the reviewed source. This storage is protected by Android's app sandbox, but the app does not add its own encryption layer to these local preferences and files. Uninstalling the app may remove local data; back up anything important first.

Weather requests send latitude and longitude to the Open-Meteo forecast API. The provider also receives normal connection metadata, such as your IP address. To display a locality name, Dislauncher passes the same coordinates to Android's Geocoder API; the implementation and any network processing depend on the Android device and its configured geocoding provider. Weather can be left disabled, and location access is not needed for ordinary app launching.

Notes are stored locally in a versioned structured rich-text format. Portable note archives contain a structured note document and any included local images; archive import enforces limits on entry count, per-entry size, total expanded size, document size, and accepted paths to reduce archive abuse and path traversal risk. Legacy text/Markdown content is migrated when opened. Local notes, tasks, calculator history, weather cache, and preferences are not encrypted by an app-managed encryption layer. Android app-private storage and sandbox protections are not the same as application-level encryption. Automatic Android backup is disabled in the app manifest, so data may be lost on uninstall or device replacement unless exported separately. Links a user explicitly opens may contact their destination, and images selected through Android's document picker remain local URI references managed by Android permissions.

See [Open-Meteo](https://open-meteo.com/) for information about the weather provider.

## Permissions and special access

| Access | Why it is used |
|---|---|
| Internet and approximate location | Optional weather requests |
| Read calendar | Show events in the agenda and Calendar screen |
| Post notifications and receive boot completed | Local reminders and rescheduling after restart |
| Notification access | Optional notification dots and active media information |
| Device Administrator | Optional double-tap-to-lock; force-lock only, no wipe-data policy declared |
| Set wallpaper | Apply a chosen image or colour to Android's Home and/or Lock screen |
| Expand status bar | Open the notification shade from a configured gesture |
| Request package deletion | Ask Android to show its app-uninstall confirmation |

Custom wallpapers and fonts use Android's system file picker; Dislauncher does not request broad external-storage access. You can deny optional permissions or revoke special access in Android settings. Features that depend on them will then be unavailable.

## APK distribution

APKs are distributed through [GitHub Releases](https://github.com/insomdroid/Dislauncher/releases/latest). Review Android's warnings and the release checksum when available. A public source repository does not guarantee an APK is authentic or bug-free; install at your own discretion.

## Contact

For questions or privacy concerns, open a [GitHub issue](https://github.com/insomdroid/Dislauncher/issues) or email [insomdroid3@gmail.com](mailto:insomdroid3@gmail.com).
