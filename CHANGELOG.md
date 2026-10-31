# Dislauncher Changelog

## 1.3.6 | 1 November 2026
#### What's New
- **Expanded Markdown Preview:** Added support for task lists, strikethrough, HTML content, and images in Notes.
- **Simplified Onboarding:** Refined the first-run experience with clearer progress, skip options, and deferred optional permissions.

#### Improvements
- Improved Books library and reader layouts, including Grid and List spacing, card sizing, full-width presentation, and add-button placement.
- Improved book processing and reading performance with persistent disk-backed page indexes, bounded cache groups, cancellable cache operations, and safer PDF resource handling.
- Improved EPUB and TXT text preservation, including paragraph breaks, chapter boundaries, escaped entities, and line spacing.
- Improved PDF cover thumbnail handling with a fallback for unavailable previews.
- Improved Notes loading and saving, Markdown editor state management, cursor tracking, and text-selection preservation.
- Improved Undo and Redo history coalescing while keeping the combined history bounded.
- Unified built-in tool headers, search fields, and shared shape tokens for a more consistent interface.
- Improved empty states and filtered results across Books, Notes, Calculator, Calendar, and Unit Converter.
- Improved app discovery and clarified selected Settings and app drawer options.
- Persisted the Books library's Grid or List view preference.

#### Fixes
- Fixed Markdown parsing edge cases and preserved note content during editing and preview.
- Fixed book page storage issues that could introduce unwanted blank lines or lose paragraph and chapter separation.
- Fixed stale or invalid persisted page indexes and cache cleanup when books are removed.
- Fixed PDF file descriptor handling and improved reader error messages.
- Fixed onboarding configuration and notification-access status handling.
- Fixed Notes editor state declarations and regression cases in Markdown history.

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
