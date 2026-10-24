# Dislauncher Changelog

This file is the single source of truth for the in-app What's New screen.
Keep release notes in English. The build copies this file into the APK; the app reads it locally and does not need a network connection.

## 1.3.0 | Unreleased
#### What's New
- **Rich-text Notes:** Create structured notes with headings, bold and italic text, checklists, lists, quotes, code blocks, links, images, labels, pinning, and note colours.
- **Portable Notes archive:** Export and import a note as an archive that preserves structured formatting and includes locally referenced images.
- **Redesigned Calendar:** Browse a month and a selected-day agenda that combines device calendar events with local tasks and reminders.
- **Improved Calculator:** Expanded scientific calculator support with parentheses, powers, trigonometric functions, logarithms, and constants.
- **Refined Unit Converter:** Convert common length, mass, temperature, speed, area, volume, time, energy, power, and data-storage units.
- **Bundled typography:** Choose Inter, Syne, Bricolage Grotesque, Doto, or Overpass Mono without downloading fonts separately.

#### Improvements
- Refined the built-in tools to keep navigation, spacing, and typography consistent with Dislauncher's minimal design.
- Preserved local-first storage for notes and reminders; device calendar access remains optional.

## 1.2.1 | 23 October 2026
#### Fixes
- Fixed Dynamic Color settings so the manual accent option clearly indicates when system colors take precedence.
- Remembered the last confirmed wallpaper target when reopening the wallpaper selection dialog.

## 1.2.0 | 22 October 2026
#### What's New
- **Improved Notes:** Write and edit notes with Markdown, preview formatting, import and export `.md` files, search note content, and pin important notes.
- **Enhanced Calendar:** View device calendar events alongside local tasks and reminders. Calendar access is requested only when you choose to enable it.
#### Improvements
- Simplified task and note lists for a cleaner, more focused experience.

## 1.1.1 | 20 October 2026
#### What's New
- **Improved Wallpaper Controls:** Apply a built-in solid color or custom image to the Home screen, Lock screen, or both from one dialog.
- **Custom System Wallpaper:** Choose an image for the Android system wallpaper independently of Dislauncher's background.
- **Refined Theme Customization:** Improved handling of System, Light, Dark, Black, and Dynamic Color modes.
- **Updated App Icon:** Refined adaptive and monochrome icon assets for supported launchers.
#### Improvements
- Separated wallpaper backgrounds from theme accent colors.
- Refined Home screen spacing and app drawer typography.
- Improved feedback when applying wallpaper.
#### Fixes
- Fixed wallpaper target selection and application feedback.
- Prevented wallpaper colors from overriding the selected theme accent.

## 1.1.0 | 19 October 2026
#### What's New
- **Built-in Unit Converter:** Convert common units directly from the app drawer.
- **Improved Icon Pack Compatibility:** Better support for icon packs using `appfilter.xml` in `raw` or `assets`, with broader theme activity discovery.
- **Refined App Drawer:** More consistent built-in app labels, two-line label support, and a cleaner built-in tools layout.
- **Built-in Tools Order:** Calendar, Notes, Calculator, and Converter.
- **Settings Shortcut:** Open Dislauncher Settings from the bottom of the app drawer.
#### Improvements
- Improved music controls and opening the active music app from the Home widget.
- Refined spacing between the clock, status widgets, music widget, and favorite apps.
- Added a subtle Home screen bounce animation when pressing Back.
#### Fixes
- Fixed an issue that could prevent the app drawer from compiling after UI changes.
- Improved icon pack theme activity handling.

## 1.0.0 | 18 October 2026
#### Features
- Minimalist Home screen and app drawer.
- Customizable app icon appearance.
- Built-in Calendar, Notes, and Calculator.
- Personalization options and launcher settings.
- Weather and media player integration.
#### Improvements
- Improved app drawer responsiveness and cached app icons to reduce repeated processing.
- Refined Home screen spacing, layouts, gestures, and Settings.
