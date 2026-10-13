# AGENTS.md — dislauncher

## Project goal
Build a minimal, text-first Android launcher inspired by the observable design and behavior of Indistractable Launcher, with our own implementation and assets. The app is named `dislauncher` (lowercase). There is no Pro edition: planned customization features are free.

The homescreen should be calm and low-distraction: time/date, optional day/year progress, compact at-a-glance information, inspiring quotes, and a user-managed list of favorite apps. The app drawer should be a fast, searchable text list with alphabet navigation. Keep normal Android status and navigation bars visible.

## UI direction
- Homescreen and app drawer: restrained, text-led design with no unnecessary cards or oversized icons.
- Settings: use modern Material 3 Expressive components and motion where it improves hierarchy and touch targets. Do not reproduce Indistractable's older settings UI.
- Accessibility: maintain readable contrast, scalable text, clear state labels, and proper touch targets.

## Planned capabilities
All planned features are free: more than four favorite apps, unlimited hidden apps, custom fonts, compatible icon packs, optional removal of default Phone/Tasks entries, app display-name overrides, automatic light/dark theme, all themes unlocked, hiding system apps, minimal widgets, inspiring quotes, and configurable gestures. Implement them in small verified milestones; do not show inert controls that imply a feature already works.

## Android development guidance
- Use Kotlin, Jetpack Compose, and Gradle Kotlin DSL unless there is a concrete reason not to.
- Read the relevant official Android skill from https://github.com/android/skills before nontrivial Android changes. Load only the relevant `SKILL.md` and linked references. Do not make the skills repository a runtime dependency or download it during builds.
- Prefer stable, mutually compatible versions, pin all dependency versions, and do not make unrelated SDK/dependency upgrades.
- Keep components small and separated by responsibility. Avoid unnecessary polling, services, permissions, or background work.
- Use platform package visibility and launcher APIs thoughtfully; do not request broad permissions without a demonstrated need.
- Test each milestone with GitHub Actions. Diagnose the first actual Gradle/Kotlin error from logs, rather than guessing.
- Confirm the latest Actions run targets the intended commit, then verify the build conclusion and APK artifact. A successful compile is not proof of on-device gesture or visual behavior.
- Do not claim behavior is device-tested unless it has actually been tested on a device.

## Repository/build contract
- Repository: `insomdroid/dislauncher`.
- Application ID: `com.insomdroid.dislauncher`.
- Compile and target SDK should be pinned explicitly and aligned with current Android platform requirements; minimum SDK should preserve reasonable device coverage.
- Keep the debug APK workflow on pushes to `main`, upload the APK as a named artifact, and avoid initializing optional tools or skill repos inside CI.
- Do not run `clean` by default; preserve Gradle caching and incremental builds.
