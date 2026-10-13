# dislauncher

A minimalist, text-first Android launcher inspired by the calm interaction model of Indistractable Launcher, implemented independently.

## Design direction

- A text-led homescreen with the clock, date, optional day/year progress, favorite apps, and optional inspiring quotes.
- A searchable app drawer opened from the homescreen by swiping left.
- A modern **Material 3 Expressive** settings screen with spacious sections, rounded surfaces, segmented theme selection, and accessible controls.
- No paid edition: planned customization features will be available for free.
- Normal Android status and navigation bars remain visible.

## Current foundation

The first milestone provides app discovery and launching, unlimited text favorites, System / Light / Dark themes, basic font choices, progress and quote toggles, and an option to hide system apps from the drawer.

Further planned features—app hiding and renaming, compatible icon packs, font importing, minimal widgets, weather, screen-time insights, and configurable gestures—will be implemented in later verified steps. Settings will only expose working controls.

## Build

Pushes to the main branch run GitHub Actions and upload a dislauncher-debug APK artifact.

The build uses JDK 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, Kotlin 2.4.10, Compose UI 1.12.1, and Material 3 1.4.0. Versions are pinned explicitly.

Successful CI compilation does not replace visual and gesture testing on a real device.
