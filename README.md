# Dislauncher

A minimalist, text-first Android launcher that keeps everyday essentials close and unnecessary clutter out of sight.

## Features

### Home screen
- A clean clock and date, with optional day and year progress indicators.
- Up to five favourite apps, with controls to arrange Home items to fit your routine.
- Optional weather, battery percentage, calendar agenda, music controls, task list, call shortcut, and Settings shortcut.
- Weather details at a glance, including current conditions, feels-like temperature, daily high and low, humidity, and wind when available.
- A saved weather reading can remain visible when a new update is unavailable or location access is turned off.
- Tap the clock or date to open your clock or calendar app.
- Choose Celsius or Fahrenheit and system, 12-hour, or 24-hour time.

### App drawer
- Browse installed apps alphabetically, with a letter rail for quick navigation.
- Search for apps by name.
- Pin up to five favourites.
- Long-press an app to rename it, hide it, open its app info, or request uninstall.
- Hide individual apps or system apps.
- Choose the system icons, a compatible installed icon pack, or one of four built-in symbol styles: Dot, Chevron, Box, and Circle.

### Personalization
- Choose a system, light, or dark theme, with Android wallpaper-based accent colors where supported.
- Dark mode uses a pure-black background.
- Use the system font, a monospace font, or import your own TTF/OTF font.
- Adjust font size and choose whether app icons appear.
- Show or hide individual Home items, the status bar, and the app drawer search bar.
- Double-tap an empty area of Home to lock the screen, or long-press empty space to open Settings.
- Swipe between Home and the app drawer, or swipe down on Home to try to open notifications.

## Why Dislauncher

- **Keep Home uncluttered:** display only the widgets and shortcuts you actually use.
- **See essentials quickly:** time, date, battery, weather, calendar events, tasks, and music controls can live on one screen.
- **Make it personal:** tune the layout, text, icons, theme, and gestures to your preferences.
- **Stay in control:** favourites, hidden apps, and optional widgets keep the launcher focused on your routine.
- **Keep simple tasks nearby:** your task list is stored locally on your device.

## Permissions and special access

Dislauncher uses access only for the features described below. You can skip optional features that need their own access.

- **Internet** (`INTERNET`) — retrieves weather data.
- **Approximate location** (`ACCESS_COARSE_LOCATION`) — finds local weather. Dislauncher does not request precise location.
- **Calendar** (`READ_CALENDAR`) — reads today's calendar events when the Home agenda is enabled.
- **Notification access** — when enabled in Android Settings, lets the music widget show the active track and control playback.
- **Device administrator** — optional; required only for the double-tap-to-lock gesture.
- **Notification shade** (`EXPAND_STATUS_BAR`) — used for the swipe-down notification gesture. Android or the device manufacturer may restrict this action.
- **Uninstall requests** (`REQUEST_DELETE_PACKAGES`) — lets you choose Uninstall from an app's long-press menu and continue through Android's confirmation screen.

Dislauncher remains usable without location, calendar, notification, or device-admin access; only the related features are unavailable or limited.
