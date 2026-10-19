# Dislauncher

A minimalist, text-first Android launcher designed to keep your Home screen focused, useful, and personal.

## Features

- **Home screen:** Clock, date, favourites, optional weather and battery information, and reorderable Home items.
- **App drawer:** Alphabetical app list, search, optional A–Z index, optional automatic keyboard, and controls for system and hidden apps.
- **App launching and icons:** Built-in icon shapes, compatible third-party icon packs, and optional app icons.
- **Built-in tools:** Calculator, Calendar with agenda, Notes, Unit Converter, Weather, and Music Player.
- **Personalization:** Light, dark, pure-black and system themes; optional Android Dynamic Color; solid or image wallpaper; adjustable app-drawer wallpaper transparency; font family and text size.
- **Gestures and access:** Double-tap-to-lock, swipe-down notification panel, optional notification dots, and settings for calendar, location, music/notification access, and device-admin features.
- **Onboarding and settings:** Guided first-run setup, bilingual interface text, favourites and hidden-app management, and launcher restart.
- **Privacy-minded controls:** Optional features can be enabled individually, with relevant Android permissions requested only when needed.

## Typography and third-party assets

Dislauncher uses **Inter Variable** as its built-in display font. The font is bundled locally in the app so it works offline and does not require network access. The existing System and Custom font choices remain available.

Inter is designed by the Inter Project Authors and is distributed under the **SIL Open Font License 1.1 (OFL-1.1)**. The full license is included at `app/src/main/assets/licenses/INTER_OFL.txt`. Source: [Inter on GitHub](https://github.com/rsms/inter) and [Google Fonts](https://fonts.google.com/specimen/Inter).

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android SDK 36
- Gradle with Kotlin DSL

## Prerequisites

- JDK 17
- Android SDK with API 36
- Android Studio, or another environment that can run the Gradle wrapper

## Quick Start

1. Clone the repository:

   ```bash
   git clone https://github.com/insomdroid/dislauncher.git
   cd dislauncher
   ```

2. Build the debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

3. Find the APK at:

   `app/build/outputs/apk/debug/app-debug.apk`

4. Install the APK on an Android device running Android 8.0 (API 26) or later.

## How to Use

1. Set Dislauncher as your default Home app when prompted.
2. Swipe to open the app drawer.
3. Search for apps or use the A–Z index to browse.
4. Open Dislauncher Settings to customize the Home screen, appearance, font, icons, gestures, and optional features.
5. Enable Android permissions only for the features you want to use.

---

Source code: [GitHub](https://github.com/insomdroid/dislauncher)  
Contact: [insomdroid3@gmail.com](mailto:insomdroid3@gmail.com)
