# dislauncher

A minimalist, text-first Android launcher inspired by the calm interaction model of Indistractable Launcher, implemented independently.

## Design direction

- A text-led homescreen with the clock, date, optional day/year progress, favorite apps, and optional inspiring quotes.
- A searchable app drawer opened from the homescreen by swiping left.
- A modern **Material 3 Expressive** settings screen with spacious sections, rounded surfaces, segmented theme selection, and accessible controls.
- No paid edition: planned customization features will be available for free.
- Normal Android status and navigation bars remain visible.

## Current foundation

The initial milestone provides app discovery and launching, unlimited text favorites, System / Light / Dark themes, basic font choices, progress and quote toggles, and an option to hide system apps from the drawer.

Further planned features—app hiding and renaming, compatible icon packs, font importing, minimal widgets, weather, screen-time insights, and configurable gestures—will be implemented in later verified steps. Settings will only expose working controls.

## Build

Pushes to the main branch run GitHub Actions, build `app-release.apk`, verify the output file, and upload the `dislauncher-release` artifact. The CI APK is signed with Android's debug key for installation/testing; it is not a production-signed release.

The build follows the verified NeoBrutalauncher-Pro CI method: JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, and Compose BOM 2025.10.00, with `compileSdk` and `targetSdk` set to 36.

## First milestone verification checklist

- [ ] Confirm all launcher activities appear in All apps.
- [ ] Tap an app to launch it.
- [ ] Long-press an app name to add or remove it from Home.
- [ ] Search by part of an application name.
- [ ] Swipe left from Home to open All apps and swipe right to return.
- [ ] Change theme and font, then reopen Settings to confirm preferences persist.
- [ ] Toggle progress, quotes, and system-app visibility.

Successful CI compilation does not replace visual and gesture testing on a real device.
