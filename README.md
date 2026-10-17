# Dislauncher

A minimalist, text-first Android launcher for a calmer Home screen. Keep the essentials close, find apps quickly, and make the screen feel like yours.

## Features

### Home
- A clean clock and date, with optional day and year progress.
- Pin up to **6 favourite apps** and arrange them on Home.
- Optional weather, battery, calendar agenda, music controls, and Settings shortcut.
- See weather details such as feels-like temperature, daily high and low, humidity, and wind when available.
- Previously loaded weather can remain visible when an update is unavailable.
- Tap the clock or date to open your clock or calendar app.
- Choose Celsius or Fahrenheit and system, 12-hour, or 24-hour time.
- Swipe between Home and the app drawer.
- Optional notification dots for selected apps.

### App drawer
- Browse apps alphabetically or jump to a letter using the A–Z index.
- Search by app name and optionally open the keyboard automatically.
- Long-press an app to rename it, add or remove it from favourites, hide it, open its info page, or start uninstalling it.
- Hide individual apps or system apps.
- Choose system icons, a compatible installed icon pack, or Dislauncher’s built-in icon styles.
- Built-in icons offer circle, outlined square, filled square, and filled rectangle styles.
- Optional built-in Calculator, Calendar, and Notes.
- Adjust custom wallpaper transparency separately for the app drawer.

### Make it yours
- System, Light, Dark, and black-friendly dark appearance options.
- Android dynamic accent colors where supported.
- Choose the system font, Dislauncher’s bundled **Plus Jakarta Sans**, or import your own TTF/OTF font.
- Custom fonts must be valid files up to **5 MB**.
- Adjust text size and show or hide app icons.
- Choose a custom wallpaper for Dislauncher, with a **10 MB** file limit. A selected wallpaper is saved for reliable use after restarting the phone.
- Optionally apply the selected image to your device’s Home screen and lock screen.
- Toggle Home elements, the status bar, and app drawer search independently.

### Gestures and everyday tools
- Double-tap Home to lock the screen (requires enabling device-admin access).
- Swipe down to open Android notifications.
- Long-press an empty area on Home to open Settings.
- Built-in Notes saves your notes automatically when you leave the page.

## Why Dislauncher?
- **Less clutter.** Keep the Home screen focused on what matters to you.
- **Quick access.** Your favourite apps and everyday information stay close.
- **Your layout, your rules.** Adjust text, icons, wallpaper, and Home elements.
- **A simpler app list.** Search, browse by letter, rename apps, and hide distractions.

## Bundled font and license

Dislauncher includes Plus Jakarta Sans by Tokotype, with Regular, Medium, and SemiBold weights bundled in the app. The font works offline and is distributed under the SIL Open Font License 1.1. The license is included at `app/src/main/assets/licenses/PLUS_JAKARTA_SANS_OFL.txt`.

Upstream font project: [tokotype/PlusJakartaSans](https://github.com/tokotype/PlusJakartaSans).

## Optional access

Some features need permission or access to be enabled in Android settings:

- **Weather:** internet access and optional location access for local weather.
- **Calendar agenda:** calendar permission.
- **Music controls and notification dots:** notification access.
- **Double-tap to lock:** device-admin access.
- **Swipe down for notifications:** Android may restrict opening the notification panel on some devices.
- **Apply wallpaper to the device:** permission to set the device wallpaper.

You can keep using Dislauncher without enabling optional features; only the features that depend on that access will be limited.

## Get involved

- **Source code:** [GitHub](https://github.com/insomdroid/dislauncher)
- **Contact:** [insomdroid3@gmail.com](mailto:insomdroid3@gmail.com)
- **Support the project:** [Ko-fi](https://ko-fi.com/insomdroid)
