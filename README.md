<p align="center">
  <img src="Screenshot/Minimalist%20Dislauncher%20Logo%20on%20Black.png" alt="Dislauncher app icon" width="112">
</p>

<h1 align="center">Dislauncher</h1>
<p align="center"><strong>Your phone. Your space.</strong></p>
<p align="center">A lightweight, text-first Android launcher designed to keep your Home screen calm and your apps close at hand.</p>
<p align="center">
  <a href="https://insomdroid.github.io/Dislauncher/"><strong>Website</strong></a>
  · <a href="https://github.com/insomdroid/Dislauncher/releases/latest"><strong>Download APK</strong></a>
  · <a href="https://github.com/insomdroid/Dislauncher/issues">Report a bug</a>
  · <a href="https://ko-fi.com/insomdroid">Support the project</a>
</p>
<p align="center">
  <a href="https://github.com/insomdroid/Dislauncher/actions/workflows/build-apk.yml"><img alt="Build APK" src="https://github.com/insomdroid/Dislauncher/actions/workflows/build-apk.yml/badge.svg?branch=main"></a>
  <a href="https://github.com/insomdroid/Dislauncher/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/insomdroid/Dislauncher"></a>
  <img alt="Minimum Android version" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84">
  <img alt="Built with Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF">
</p>

Dislauncher is an independent, open-source personal project built with AI assistance. It is shared freely for anyone who finds it useful.

## A quick look

<p align="center">
  <img src="screenshots/home-dark.png" width="230" alt="Dislauncher Home screen in dark mode">
  &nbsp;&nbsp;
  <img src="screenshots/home-light.png" width="230" alt="Dislauncher Home screen in light mode">
  &nbsp;&nbsp;
  <img src="screenshots/app-drawer.png" width="230" alt="Dislauncher searchable app drawer">
</p>

## Features

- **Minimal Home screen:** clock, date, progress indicators and favourite apps.
- **App drawer:** search, alphabetical browsing, favourites, hidden apps and compatible icon packs.
- **Personalisation:** System, Light, Dark and Black modes, Dynamic Color, accent colours, wallpapers, fonts and text sizing.
- **Everyday tools:** calendar and local reminders, notes, calculator, unit converter, optional weather and music controls.
- **Optional system access:** use core launcher features without enabling every permission.

See the [website](https://insomdroid.github.io/Dislauncher/) for screenshots and a visual overview.

## Install

1. Open the [latest GitHub Release](https://github.com/insomdroid/Dislauncher/releases/latest).
2. Download the APK listed under **Assets**.
3. Open it and follow Android's installation prompts. Choose Dislauncher as your default Home app to use it as your launcher.

Download only from the official release page. See [APK safety](APK_SAFETY.md) if you need more detail.

## Privacy, permissions and safety

Dislauncher has no account system, advertising SDK or analytics SDK in the reviewed source. Weather is optional and sends approximate coordinates to Open-Meteo when you enable it. Other optional access supports features such as calendar, reminders, notification dots, music controls and double-tap-to-lock.

- [Privacy and permissions](PRIVACY.md)
- [APK safety and updates](APK_SAFETY.md)

## Build

Dislauncher uses Kotlin, Jetpack Compose, Material 3 and Android SDK 36. It supports Android 8.0 (API 26) and later.

For a quick local debug build:

```sh
./gradlew assembleDebug
```

See [BUILDING.md](BUILDING.md) for requirements and CI/release details. Official release APKs are published on [GitHub Releases](https://github.com/insomdroid/Dislauncher/releases/latest).

## Project links

- [Website](https://insomdroid.github.io/Dislauncher/)
- [Source code](https://github.com/insomdroid/Dislauncher)
- [Issues and feedback](https://github.com/insomdroid/Dislauncher/issues)
- [Support on Ko-fi](https://ko-fi.com/insomdroid)

## License

The project code is licensed under the [MIT License](LICENSE). Third-party assets and dependencies may have their own licenses; the bundled Inter font is covered by SIL Open Font License 1.1, included at `app/src/main/assets/licenses/INTER_OFL.txt`.
