# Dislauncher Privacy Notes

**Last reviewed:** 20 October 2026  
**Project:** Dislauncher, an independent Android launcher

Dislauncher is a personal, AI-assisted learning project. This document describes data handling visible in the current source tree. Android versions, manufacturer services and third-party providers may handle data under their own policies.

## Summary

- Dislauncher has no account or sign-in system.
- The source reviewed contains no advertising SDK or analytics/telemetry SDK and no Dislauncher-operated account server.
- Preferences and local content such as notes, reminders and imported custom fonts are stored on the device.
- Weather is the main feature that makes a network request. If you enable Weather and grant approximate location, the app sends coordinates to the Open-Meteo API over HTTPS to retrieve weather data.
- Calendar, location, notification-listener and Device Administrator access are optional feature access; the core launcher can be used without enabling all of them.

## Data stored on the device

Depending on the features you use, Dislauncher stores launcher preferences, favourites and hidden-app choices, custom labels, appearance choices, wallpaper/font preferences, weather cache, notes and local reminder data in app-private storage or Android preferences. A selected custom font is copied into app-private storage after basic format and size validation.

This data is not uploaded to a Dislauncher-operated server in the source currently reviewed. Uninstalling the app may remove app-private data; back up anything important before uninstalling or switching builds.

## Network and third-party services

### Weather — Open-Meteo

When Weather is used and approximate location permission is granted, Dislauncher obtains a recent approximate location where available and sends latitude/longitude in an HTTPS request to the Open-Meteo forecast API. The request includes the selected temperature unit and asks for current and daily forecast fields. The service necessarily receives request metadata, such as the IP address used to connect, as well as the coordinates included in the request.

Weather may not work if location access is denied, location is unavailable, or the provider/network is unavailable. Weather is optional; the launcher does not need location access for ordinary app launching.

See [Open-Meteo](https://open-meteo.com/) for information about the external weather provider and its terms.

## Android permissions and special access

| Access | Purpose |
|---|---|
| Internet | Requests weather data from Open-Meteo when the Weather feature is used. |
| Approximate location | Obtains coordinates for weather; coordinates are sent to Open-Meteo for the forecast request. |
| Read calendar | Reads calendar events for the agenda and Calendar screen. No calendar-write permission is declared. |
| Post notifications | Displays local reminders on Android versions that require notification permission. |
| Receive boot completed | Reschedules future local reminders after a device restart. |
| Wallpaper | Applies the selected image or colour to Android's Home and/or Lock screen wallpaper. |
| Expand status bar | Opens the notification shade from the configured swipe-down gesture. |
| Request package deletion | Invokes Android's system uninstall confirmation for supported app-management actions; Android controls the confirmation. |
| Notification access | When enabled by the user in Android settings, supports selected notification dots and the music widget's active media-session information. Notification access is broad Android special access; review the system description before granting it. |
| Device Administrator | When enabled by the user, allows the double-tap-to-lock feature. The declared device-admin policy is force-lock only; the app does not declare a wipe-data policy. |

Custom wallpapers and fonts are selected through Android's system file picker. Dislauncher does not declare broad external-storage permission.

You can deny optional runtime permissions or revoke special access in Android settings. Features that depend on that access will be unavailable until access is granted again.

## Children, advertising and tracking

The source reviewed does not include an ad SDK, analytics SDK, account system or behavioural tracking service. Dislauncher is not designed to collect a profile of its users. The Weather feature's external request is described above.

## Security and APK distribution

Dislauncher APKs are distributed through GitHub Releases rather than Google Play. Google Play review and Play Store distribution protections do not apply in the same way to direct APK installation. Download only from the official [Releases page](https://github.com/insomdroid/dislauncher/releases/latest), review Android's warnings carefully, and compare a release checksum when provided. A public source repository does not guarantee that a downloaded APK is authentic or bug-free.

## Changes to this document

This document should be updated if the app adds new permissions, analytics, advertising, new network services or other meaningful data flows. The statements above reflect the source reviewed on the date shown, not a promise about future versions.

## Contact

For questions or privacy concerns, open a [GitHub issue](https://github.com/insomdroid/dislauncher/issues) or contact [insomdroid3@gmail.com](mailto:insomdroid3@gmail.com).
