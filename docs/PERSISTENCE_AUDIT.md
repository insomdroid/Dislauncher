# Persistence Audit and Manual Test Plan

Date: 2026-10-23  
Scope: theme/color, fonts, wallpaper, Home favourites/order, folders, hidden apps, and notification dots.

## Audit method and limits

This is a source-level audit of the current `main` implementation. The cases below are a manual verification plan with expected outcomes; they are **not** evidence that the app has been run on a device or emulator. The repository currently has no dedicated `app/src/test` or `app/src/androidTest` suite for these flows. A successful CI build/lint is necessary but does not prove runtime persistence.

## Changes made in this audit

- When Android Dynamic Color is supported and enabled, the manual accent selector is replaced by an explanatory label. The stored manual accent preset is retained and becomes selectable again when Dynamic Color is disabled.
- The wallpaper target dialog now initializes from `wallpaperTarget`; opening the dialog no longer resets it to `both`. Confirming the selection saves it through `LauncherPreferences.save`, so the next dialog opens with the last confirmed target. Cancel does not save a new target.

## Source-level observations

### Theme and color

- `LauncherPreferences.save/load` stores `themeMode`, `dynamicColor`, and `accentPreset`; theme migration version 1 maps the legacy `dark` mode to `black`.
- The active accent scheme prioritizes Dynamic Color when it is supported and enabled; otherwise it uses the selected manual preset.
- Font size is read with a fallback in the theme renderer, but unlike theme mode and accent preset, its loaded string is not normalized to an allow-list. UI selection currently offers only Small, Medium, and Large.

### Fonts

- Font mode and custom font display name are stored in SharedPreferences. The renderer loads custom font bytes from the app-private files directory, not from the display name itself.
- Verify both the preference and the private font file survive force-stop/relaunch and launcher restart. Also verify fallback to the system font if a custom font file is missing or invalid.

### Wallpaper

- Wallpaper mode, internal URI, and target are stored in `LauncherPreferences`.
- Selected images are copied into app-private storage for reliable reload; legacy/provider URIs are migrated when possible. Decode/migration failure resets to the default wallpaper and clears the stored URI.
- Applying a wallpaper to both device screens attempts Home and Lock separately, so partial success should be reported accurately.
- The internal launcher background and the Android device wallpaper are separate behaviors; tests should check both independently.

### Favourites and order

- Favourite package IDs are stored separately as `favorite_packages`; Home ordering is stored separately by the home-item-order helpers.
- Loading caps favourites at six, and a cleanup writes back if more than six were stored. Removed/uninstalled packages are reconciled against available apps.
- Verify that favourite membership and display order both survive restart; membership alone is not enough to prove order persistence.

### Folders

- Folder names and package-to-folder assignments are stored in `LauncherPreferences` as string sets.
- The Manage Folders flow prevents adding an app that already has an assignment. The App Drawer quick “Add to folder” flow should also be tested for the one-app/one-folder invariant: source inspection shows it adds an assignment without first removing a previous assignment for that package. If reproduced, this is a consistency bug to fix separately.
- Deleting a folder is expected to remove its assignments only, not uninstall the apps.

### Hidden apps

- Hidden package IDs are stored in the separate `hidden_packages` set and restored at launcher startup.
- Hiding an app is presentation-only; it should not uninstall or disable the package. Verify unhide restores it to the drawer.

### Notification dots

- Dot enabled state, selected package IDs, and dot color are stored in `LauncherPreferences`.
- Changes to enabled state or selected packages explicitly refresh notification-dot state. Verify color updates independently and that only selected apps show dots when the feature is enabled.
- Android notification-listener access is an external system permission; it can be revoked independently of the saved preference and must be tested separately from preference persistence.

## Manual test cases

Use a clean baseline only where stated. For persistence tests, change one setting, leave Settings, force-stop/reopen the launcher or use Restart Launcher, and confirm the value remains. Where relevant, also reboot the device.

| ID | Area | Steps | Expected result |
|---|---|---|---|
| TH-01 | Theme | Select Light, leave Settings and restart launcher | Light mode remains active |
| TH-02 | Theme | Select Dark, restart launcher | Dark mode remains active |
| TH-03 | Theme | Select AMOLED, restart launcher | Black background mode remains active |
| TH-04 | Theme migration | Upgrade with legacy `theme_mode=dark` and migration version below 1 | Legacy value migrates to AMOLED/black once; subsequent loads do not remigrate |
| CL-01 | Dynamic Color | Enable Dynamic Color on Android 12+ | Manual accent choices are replaced by explanatory text; wallpaper-derived accent is used |
| CL-02 | Accent preset | Set Red, enable Dynamic Color, then disable Dynamic Color | Red preset is retained and becomes active/selectable again |
| CL-03 | Unsupported Android | Run below Android 12 with Dynamic Color preference enabled | Manual accent selector remains available because platform Dynamic Color is unsupported |
| FN-01 | Built-in font | Select Inter Variable, restart launcher | Inter Variable remains selected and is rendered |
| FN-02 | Custom font | Import valid TTF/OTF, restart launcher | Custom font remains selected and loads from app-private storage |
| FN-03 | Custom font fallback | With test data, remove/rename stored custom font file while mode remains custom, then restart | Launcher falls back to system font without crashing; settings still indicate custom mode until changed |
| FN-04 | Font size | Test Small, Medium, Large separately with restart | Each selection remains and applies its expected scale |
| WP-01 | Image wallpaper | Choose an image, restart launcher | Selected image reloads from app-private storage |
| WP-02 | Wallpaper target | Choose Home, confirm, reopen target dialog | Home remains selected |
| WP-03 | Wallpaper target | Choose Lock screen, confirm, reopen dialog | Lock screen remains selected |
| WP-04 | Wallpaper target | Choose Both, confirm, reopen dialog | Both screens remains selected |
| WP-05 | Wallpaper target | Change selection then press Cancel; reopen dialog | Previously confirmed target remains selected |
| WP-06 | Wallpaper apply | Apply to Both with permissions/system support | Home and Lock both update; success or partial-failure message matches actual result |
| WP-07 | Wallpaper fallback | Test with a deliberately unreadable legacy/provider URI in a test build | Default launcher wallpaper is restored and stale URI is cleared, without a crash |
| FV-01 | Favourites | Select up to six apps, restart launcher | Same favourite membership remains |
| FV-02 | Favourites limit | Attempt to add a seventh app | Addition is rejected and user sees the six-app limit message |
| FV-03 | Favourite order | Reorder favourites, restart launcher | Display order is unchanged |
| FV-04 | Uninstall/reconcile | Favourite an app, uninstall it, then refresh/restart | Unavailable package is removed from favourites/order |
| FD-01 | Folder persistence | Create folder and assign app, restart launcher | Folder and assignment remain |
| FD-02 | Folder invariant | Assign one app to Folder A, then use the App Drawer quick action to add it to Folder B | App should belong to only one folder; source review indicates this path may currently retain both assignments and needs verification |
| FD-03 | Folder delete | Delete a folder containing apps | Folder/assignments disappear; installed apps remain installed and visible in the main drawer |
| FD-04 | Folder remove | Remove an app from a folder, restart launcher | App is unassigned and remains installed/visible |
| HD-01 | Hidden app | Hide an app, restart launcher | App remains hidden from the drawer |
| HD-02 | Hidden app | Unhide that app, restart launcher | App returns to the drawer |
| HD-03 | Hide system apps | Toggle Hide system apps, restart launcher | System-app visibility matches the saved toggle; this is independent of the hidden-app list |
| ND-01 | Notification dots | Enable dots, select an app, restart launcher | Enabled state and selected package remain |
| ND-02 | Notification dots | Select only App A, trigger eligible notifications for A and B | Dot appears for A only, assuming notification access is granted |
| ND-03 | Notification dots | Change dot color, then restart launcher | Chosen color remains and is reflected on dots |
| ND-04 | Notification access | Revoke notification-listener access in Android Settings | Saved dot preferences remain, but notification-derived dots may stop until access is restored |
| ND-05 | Notification selection | Remove App A from selected packages, then trigger an eligible notification | App A no longer shows a dot |

## Recommended execution order

1. Run TH-01–TH-04 and CL-01–CL-03 first, including Android versions with and without platform Dynamic Color.
2. Run WP-01–WP-07, especially dialog target restoration and partial apply failures.
3. Run FV-01–FV-04 and FD-01–FD-04; prioritize checking FD-02 because it may reveal an assignment inconsistency.
4. Run FN-01–FN-04 and HD-01–HD-03.
5. Run ND-01–ND-05 with notification access granted, then revoked.

Record device model, Android version, app build, steps, actual result, and whether the result differs after a force-stop versus a full device reboot.
