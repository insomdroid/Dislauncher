# Dislauncher

Dislauncher is a lightweight, text-first Android launcher designed to keep your Home screen calm and your apps close at hand. It combines a minimal interface with useful everyday tools, without making icons and visual effects the focus.

## Screenshots

<p align="center">
  <img src="Screenshot/Screenshot_20261022-221222_Dislauncher.png" alt="Dislauncher screenshot 1" width="22%" />
  <img src="Screenshot/Screenshot_20261022-221233_Dislauncher.png" alt="Dislauncher screenshot 2" width="22%" />
  <img src="Screenshot/Screenshot_20261022-221259_Dislauncher.png" alt="Dislauncher screenshot 3" width="22%" />
  <img src="Screenshot/Screenshot_20261022-221309_Dislauncher.png" alt="Dislauncher screenshot 4" width="22%" />
  <img src="Screenshot/Screenshot_20261022-221316_Dislauncher.png" alt="Dislauncher screenshot 5" width="22%" />
  <img src="Screenshot/Screenshot_20261022-221321_Dislauncher.png" alt="Dislauncher screenshot 6" width="22%" />
  <img src="Screenshot/Screenshot_20261022-221335_Dislauncher.png" alt="Dislauncher screenshot 7" width="22%" />
  <img src="Screenshot/Screenshot_20261022-221343_Dislauncher.png" alt="Dislauncher screenshot 8" width="22%" />
</p>

## Key features

- **Minimal Home screen:** clock, date, progress indicators, and favourite apps in a text-first layout.
- **App drawer:** searchable alphabetical app list, favourites, folders, hidden apps, and compatible icon packs.
- **Personalisation:** System, Light, Dark, and Black themes, Dynamic Color, accent colours, wallpapers, built-in fonts (Inter, Spline Sans, Outfit, DM Sans, and Geist), custom fonts, and text sizing.
- **Everyday tools:** built-in calendar and local reminders, Markdown notes, calculator, unit converter, optional weather, and music controls.
- **Useful shortcuts:** notification dots, app actions, and optional gestures for quick access to common tasks.
- **Privacy-minded:** no account system, advertising SDK, or analytics SDK in the reviewed source. Optional features may request permissions or connect to external services.

## Download

Get the latest APK from [GitHub Releases](https://github.com/insomdroid/Dislauncher/releases/latest).

1. Open the latest release and download the APK from **Assets**.
2. Open the downloaded file and follow Android's installation prompts.
3. Select Dislauncher as your default Home app.

Download only from the official release page. See [APK safety and updates](APK_SAFETY.md) for more information.

## Tech stack

- Kotlin
- Jetpack Compose and Material 3
- Android SDK 36
- Minimum Android version: Android 8.0 (API 26)

To build a local debug APK:

```sh
./gradlew assembleDebug
```

See [BUILDING.md](BUILDING.md) for build requirements and release details.

## Privacy and permissions

Core launcher features can be used without enabling every optional permission. Weather is optional and sends approximate coordinates to Open-Meteo when enabled. Other optional access supports features such as calendar, reminders, notification dots, music controls, and double-tap-to-lock.

- [Privacy and permissions](PRIVACY.md)
- [APK safety and updates](APK_SAFETY.md)

## Links

- [Project website](https://insomdroid.github.io/Dislauncher/)
- [Source code](https://github.com/insomdroid/Dislauncher)
- [Report a bug or request a feature](https://github.com/insomdroid/Dislauncher/issues)
- [Support the project](https://ko-fi.com/insomdroid)

## Fonts and licensing

Inter, Urbanist, Sora, Spline Sans, Outfit, DM Sans, and Geist are bundled with the app. Font files are stored in the repository and decoded into Android resources during the local build, so builds do not download fonts from the network. The built-in font selection applies across the launcher interface.

The bundled fonts are licensed under the SIL Open Font License 1.1. Copyright notices, source repositories, and the consolidated license text are in [FONT_LICENSES.txt](app/src/main/assets/licenses/FONT_LICENSES.txt). Urbanist is the geometric, modern alternative to Satoshi; Sora provides a more expressive alternative to Chillax. Both are available under SIL Open Font License 1.1 and can be redistributed with the app.

## License

Dislauncher source code is licensed under the [MIT License](LICENSE). Third-party fonts and dependencies remain under their respective licenses; the app's MIT license does not replace those terms.
