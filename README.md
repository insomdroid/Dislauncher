# Dislauncher

A minimalist, text-first Android launcher designed to keep your home screen calm, clean, and focused.

## Home screen

- A simple clock and date with optional day and year progress indicators.
- Keep favourite apps close at hand.
- Add only the information and shortcuts you want: weather, battery, tasks, calls, music, calendar, and Settings.
- Tap the clock to open your clock app or the date to open your calendar.
- Choose a clean layout with adjustable widget visibility and spacing.
- Weather readings are saved locally after a successful update, so the last reading can remain visible when location permission is disabled.

## App drawer

- Browse apps in alphabetical order.
- Quickly find apps with search.
- Hide apps you don't want in your drawer, or hide system apps.
- Pin favourites, rename apps, and access app information from app actions.
- Personalize app icons with supported icon packs.

## Make it yours

- Choose a font and adjust its size.
- Switch between light, dark, system, and automatic sunrise/sunset themes.
- Choose Celsius or Fahrenheit and your preferred time format.
- Customize which Home screen elements and shortcuts are shown.
- Reorder Home screen items to suit your routine.

## Handy gestures

- Swipe between Home and the app drawer.
- Swipe down to open notifications.
- Double-tap an empty area of Home to lock your screen.
- Long-press an empty area of Home to open Settings.

## Launcher preferences

Settings are saved locally and let you configure:

- Visibility of the clock, date, weather, battery, status bar, search bar, day/year progress, tasks, calls, music, calendar shortcut, and Settings shortcut.
- Celsius or Fahrenheit, plus system, 12-hour, or 24-hour time formats.
- App icons, compatible icon packs, font family, font size, and theme.
- Placement of the year progress indicator near the clock.
- Double-tap-to-lock, swipe-down notification gesture, automatic keyboard opening in search, and whether system apps are hidden.
- Hidden apps and favourites, Android Settings, and the default Home app.

## Permissions and limitations

- **Weather:** Location permission and an internet connection are required to refresh weather. The most recent successful reading is saved locally and can remain visible if location permission is later disabled. Weather data comes from Open-Meteo. Android Geocoder is used on a best-effort basis to label the approximate location. Sunrise and sunset times from the weather response are used for the automatic solar theme.
- **Calendar agenda:** If enabled, calendar access is required to show today's remaining events. Events can be opened in the system calendar.
- **Music controls:** Media-session controls may require enabling Dislauncher in Android's notification-listener settings.
- **Double-tap to lock:** Android requires you to explicitly enable Dislauncher as a device administrator.
- **Swipe down for notifications:** This is best-effort. Android versions and device manufacturers may prevent third-party apps from expanding the system notification shade.
- **Icon packs:** Only installed icon packs with compatible ADW/Nova-style `appfilter.xml` entries are supported. Otherwise, the system app icon is shown.
- **Fonts:** Open Sans and Raleway Dots are bundled from the Google Fonts repository at a pinned revision, with their OFL license files included in the APK assets. Menlo falls back to Android's monospace family on devices without Menlo.

## Build configuration

GitHub Actions builds `app-release.apk`, verifies that the APK exists, and uploads the `dislauncher-release` artifact. The CI APK is signed with Android's debug key for installation and testing; it is not a production-signed release.

- JDK 17
- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.2.21
- Compose BOM 2025.10.00
- `compileSdk` 36 and `targetSdk` 36

Dislauncher keeps the essentials within reach while letting you leave out the clutter.
