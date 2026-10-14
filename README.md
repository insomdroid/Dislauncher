# dislauncher

A minimalist, text-first Android launcher focused on a calm, low-distraction experience.

## Home screen

- Compact text-first layout for the clock, date, favourite apps, battery, and optional progress indicators.
- Tap the clock to open the clock app; tap the date to open the calendar app. The optional Calendar agenda requests calendar permission and displays today's remaining events, with tap-through to the system calendar.
- Long-press empty space to open Settings with haptic feedback.
- Optional double-tap-to-lock, year/day progress, quote, weather, screen-time, tasks, call, music, and calendar shortcut.
- Font families: Roboto, Open Sans, Monospace, Menlo-compatible, Raleway Dots, Sans-serif Light, and Serif; Small, Medium, and Large sizes.
- Optional app icons and compatible icon packs using the ADW/Nova-style `appfilter.xml` format.
- Theme choices: follow system, automatic sunrise/sunset, Black, and White.

## App drawer

- Home and drawer use a horizontal pager for native Compose swipe animation.
- Alphabetically ordered applications with no letter-category headings.
- Optional search field, keyboard auto-launch, app icons, and touch-activated letter scrubber.
- Long-press an app to open actions for launching, favourites, renaming, hiding, app info, and uninstalling non-system apps.
- Manage hidden apps and favourites in Settings.

## Launcher preferences

Settings are persisted locally and include:

- Show/hide clock, date, weather, battery, status bar, search bar, character scrubber, day/year progress, quote, screen time, tasks, call, music, and Settings shortcut.
- Celsius/Fahrenheit and system/12-hour/24-hour time formats.
- Show app icons, icon pack, font family, font size, and theme.
- Pin the year progress near the clock.
- Double-tap to lock, swipe down to notifications, auto-launch keyboard, hide system apps, manage hidden apps/favourites, open Android Settings, and select the default Home app.

## Permissions and limitations

- **Weather:** requires location permission and an internet connection. Weather data comes from Open-Meteo; Android Geocoder is used on a best-effort basis to label the approximate current location. Sunrise/sunset times from the same weather response drive the automatic solar theme.
- **Screen time:** Android's Usage Access permission must be granted in system settings.
- **Double-tap to lock:** Android asks the user to explicitly enable dislauncher as a device administrator.
- **Swipe down for notifications:** this is best-effort; Android versions and device manufacturers can block third-party apps from expanding the system notification shade.
- **Icon packs:** only installed icon packs that expose compatible ADW/Nova-style `appfilter.xml` entries are used; otherwise, the system app icon is shown.
- **Fonts:** Open Sans and Raleway Dots are bundled from the Google Fonts repository at a pinned revision; their OFL license files are included in the APK assets. Menlo falls back to Android's monospace family on devices without Menlo.
- Successful CI compilation does not replace real-device interaction, visual, or permission testing.

## Build

GitHub Actions builds `app-release.apk`, verifies that it exists, and uploads the `dislauncher-release` artifact. The CI APK is signed with Android's debug key for installation/testing; it is not a production-signed release.

The build uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, and Compose BOM 2025.10.00, with `compileSdk` and `targetSdk` set to 36.
