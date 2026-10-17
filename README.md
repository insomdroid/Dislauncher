# Dislauncher

A minimalist, text-first Android launcher for a calmer Home screen. Keep the essentials visible, your apps easy to reach, and the clutter out of the way.

## Screenshots


## Features

### Home screen
- Clean clock and date, with optional day and year progress indicators.
- Pin up to six favourite apps and reorder them on Home.
- Optional weather, battery percentage, calendar agenda, music controls, and Settings shortcut.
- Weather page with current conditions and details such as feels-like temperature, daily high and low, humidity, and wind when available.
- Cached weather can remain visible when a refresh is unavailable.
- Tap the clock or date to open the corresponding clock or calendar app.
- Choose Celsius or Fahrenheit and system, 12-hour, or 24-hour time.
- Swipe between Home and the app drawer.

### App drawer
- Browse installed apps alphabetically, with a letter rail for quick navigation.
- Search apps by name, with an option to open the keyboard automatically.
- Long-press an app to rename it, add or remove it from favourites, hide it, open app info, or request uninstall.
- Hide individual apps or system apps.
- Use compatible installed icon packs or keep system icons.

### Personalization
- Choose System, Light, Dark, or pure-black themes.
- Use Android dynamic colors for accents where supported.
- Choose the Android system font, Dislauncher’s bundled Plus Jakarta Sans, or import a custom TTF/OTF font.
- Plus Jakarta Sans is bundled in the app, so it works offline and does not need to be downloaded during the build.
- Adjust font size and show or hide app icons.
- Customize icon shapes when built-in icons are enabled.
- Toggle Home elements, the status bar, and app drawer search independently.
- Double-tap Home to lock the screen, long-press empty Home space to open Settings, or swipe down to open notifications.

## Why Dislauncher

- **Less clutter:** keep only the Home items you actually use.
- **At-a-glance essentials:** put time, date, weather, calendar events, battery, and music controls within easy reach.
- **Your layout, your rules:** organize favourites and adjust text, icons, colors, and gestures.
- **A focused app list:** find apps quickly and hide the ones you do not want in view.
- **No unnecessary complexity:** everyday launcher actions stay straightforward.

## Bundled font and license

Dislauncher includes Plus Jakarta Sans by Tokotype, with Regular, Medium, and SemiBold weights bundled directly in the repository and APK. The font is distributed under the SIL Open Font License 1.1; its license is included in `app/src/main/assets/licenses/PLUS_JAKARTA_SANS_OFL.txt`. Upstream source: [tokotype/PlusJakartaSans](https://github.com/tokotype/PlusJakartaSans).

## Permissions and special access

Dislauncher only uses access needed for its listed features. Optional features can be left disabled.

- **Internet** (`INTERNET`) — retrieves weather data.
- **Approximate location** (`ACCESS_COARSE_LOCATION`) — gets local weather. Precise location is not requested.
- **Calendar** (`READ_CALENDAR`) — reads calendar events for the Home agenda.
- **Notification access** — optional access enabled in Android Settings so the music widget can show the active track and control playback.
- **Device administrator** — optional; required for the double-tap-to-lock gesture.
- **Notification shade** (`EXPAND_STATUS_BAR`) — used by the swipe-down notification gesture. Android or the device manufacturer may restrict this action.
- **Uninstall requests** (`REQUEST_DELETE_PACKAGES`) — lets you start an uninstall from an app's long-press menu; Android still shows its confirmation flow.

Dislauncher remains usable without location, calendar, notification, or device-admin access. Only the features that depend on the unavailable access are limited.
