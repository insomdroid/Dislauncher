# Dislauncher Changelog

## 1.3.5 | 30 October 2026
#### What's New
- **Built-in Books Reader:** Read EPUB, PDF, and TXT files offline, with Grid and List library views.
- **Home Clock Styles:** Choose from Vertical Stack, Binary Dot, Dual Bar, and Text clock layouts.

#### Improvements
- Refined the layout and visual consistency across built-in tools.
- Improved the Books library, cover handling, reading controls, and page navigation.
- Improved book loading and memory usage with disk-backed page storage and bounded processing.
- Improved Markdown editing, including list continuation and formatting preservation.
- Added separate Undo and Redo history with a 50-step limit.
- Improved reminder scheduling when the device clock or time zone changes.
- Improved app drawer ordering and folder filtering performance.
- Improved wallpaper contrast calculation efficiency.
- Added clearer onboarding explanations for permissions and handling of permanently denied permissions.
- Reduced unnecessary preference writes.

#### Fixes
- Fixed Books reader layout, scrolling, and navigation issues.
- Fixed Markdown parsing and note round-trip preservation.
- Protected notes and calendar data from being overwritten when stored data is corrupted.
- Fixed built-in tool icon contrast in dark and AMOLED themes.
- Fixed Books library text and menu visibility in List view.
- Fixed reminder scheduling after system time and time zone changes.

## 1.3.4 | 28 October 2026
#### Improvements
- Improved app drawer alphabet navigation responsiveness.
- Added a direct shortcut to the top of the app list in the alphabet scrubber.
- Standardized accent presets and notification dot colors for more consistent theming.
- Added optional descriptions to Settings choices and Home screen settings.

#### Fixes
- Fixed built-in tool icon contrast in Light mode.

## 1.3.3 | 25 October 2026
#### What's New
- **Geist Mono:** Replaced Overpass Mono with the more compact Geist Mono font, bundled locally for offline use.
- **Network Information:** Added a setting to show or hide network information on the Home screen.

#### Improvements
- Refined Settings category spacing, option layout, and action states for a more consistent interface.
- Simplified the Home screen status row and removed unnecessary separators.
- Improved organization of Home, Apps, and Access settings.

#### Fixes
- Fixed a missing Compose import that prevented the updated Settings screen from compiling.
- Corrected the weather widget's click target and improved the network status label's permission handling.

This file is the single source of truth for the in-app What's New screen.
Keep release notes in English. The build copies this file into the APK; the app reads it locally and does not need a network connection.

## 1.3.1 | 24 October 2026
#### What's New
- **Redesigned Notes:** Improved the notes experience with structured rich-text editing, Markdown support, note search, and pinning.
- **Portable Notes Archive:** Import and export notes using Markdown files or archives containing notes and their locally referenced images.
- **Redesigned Calendar:** Browse a monthly calendar and view an agenda for the selected date.
- **Combined Calendar Agenda:** View device calendar events alongside local tasks and reminders.
- **Bundled Typography:** Choose Inter, Syne, Bricolage Grotesque, Doto, or Overpass Mono without downloading fonts separately.

#### Improvements
- Refined the built-in Notes and Calendar layouts to maintain Dislauncher's minimal design.
- Kept notes and local reminders stored on-device while making device calendar access optional.
- Updated the Kotlin compiler configuration for compatibility with the current build setup.
- Simplified the APK build workflow by removing unnecessary Compose compiler report generation and upload steps.

#### Fixes
- Fixed rich-text formatting so actions such as bold and italic correctly apply to selected text.
- Improved build configuration compatibility with the selected Gradle and Kotlin toolchain.

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
