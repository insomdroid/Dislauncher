# ChatGPT Development Guide — Dislauncher

Repository: https://github.com/insomdroid/Dislauncher

This guide defines a practical workflow for using ChatGPT and GitHub to develop Dislauncher safely from a phone. The repository is the source of truth; chat history is supporting context only.

## Product direction

**Minimal. Functional. Personal.**

Dislauncher is a text-first Android launcher. Preserve its established visual language, existing user data, release compatibility, and approved interaction patterns.

## Non-negotiable working rules

- Work only in this repository. Never reuse implementation assumptions from Nomina, Icon-Packs, or Omamu without verifying that they apply.
- Inspect the current default branch, relevant source files, Gradle configuration, manifest, and workflows before editing.
- Prefer small, focused changes on `main` for routine work. Do not create a pull request unless requested or required by repository policy.
- Do not invent package names, versions, dependencies, file paths, APIs, or current implementation details.
- Do not perform opportunistic redesigns, refactors, dependency upgrades, or formatting sweeps outside the agreed scope.
- Preserve application ID, signing/release configuration, versioning conventions, preference keys, databases, and migration behavior unless the task explicitly requires a change.
- Keep test-only code out of release APKs. Keep useful repository tests when appropriate.
- A successful Gradle build is not proof that the feature works on a device.

## Recommended task workflow

1. **Define one outcome.** State the exact behavior or visual issue to change and what must remain unchanged.
2. **Inspect first.** Read the current implementation and every direct caller/state owner of the affected code. Check recent commits and the relevant workflow.
3. **Plan briefly.** Identify the smallest file set, side effects, compatibility risks, and a concrete verification method.
4. **Implement only the approved scope.** Avoid unrelated cleanup. Review the complete diff before committing.
5. **Build with GitHub Actions.** Record the workflow run URL, commit SHA, artifact name, and actual result.
6. **Install and verify on Android.** Test the requested path plus nearby interactions and both light/dark themes when relevant.
7. **Checkpoint.** Record the successful commit, build run, what was tested, remaining defects, and the next single task.

## Dislauncher-specific checks

- Launcher behavior: default launcher selection, app list refresh, app launch, favorites, search, and returning to Home.
- Home information: date/time format, network and battery state, weather state, and empty states. Avoid adding decorative separators or icons contrary to the approved text-first layout.
- Built-in tools: Notes, Calendar, Calculator, Reader, and Converter must retain their established navigation and interaction patterns.
- Notes: verify persistence, Markdown behavior, empty-note discard, and undo/redo boundaries when touched.
- Reader: verify EPUB/PDF/TXT loading, file-size limits, reading width, list/grid persistence, and dark-theme contrast when touched.
- Notifications: verify notification dots and listener behavior without introducing unnecessary background work.
- Clock styles: compare screenshots against the approved preview and verify alignment, typography, and date/time hierarchy.
- Performance: watch for repeated preference reads, unnecessary recomposition, leaked listeners, unclosed resources, and expensive work on the main thread.
- Release: verify that an update installs over the previous release without uninstalling or losing user data.

## Screenshot-based UI review

Before a visual change, capture the current screen. After the build, capture the same screen using the same device, theme, font scale, and state. Ask ChatGPT to compare alignment, spacing, text size, clipping, contrast, and unintended changes. Do not accept a visual fix based only on source-code appearance.

## Copy-ready prompts

### Audit before editing
> Inspect the current Dislauncher repository and the exact feature I describe. Do not edit anything yet. Report the current implementation with file paths, the smallest safe change, affected callers/state, compatibility risks, and how to verify it on a device and in GitHub Actions. Do not guess missing facts.

### Implement one approved change
> Implement only the agreed Dislauncher change. Preserve unrelated behavior and existing preference/data keys. Review the full diff for unintended changes. Commit directly to main if the repository permissions allow it. Report changed files, commit SHA, workflow run URL, and verification status. Do not claim the APK works until it has been installed and tested.

### Diagnose a failed build
> Diagnose this GitHub Actions failure from the first meaningful error, not merely the final BUILD FAILED line. Identify the exact root cause and affected file(s). Make the smallest correction, inspect the diff, and rerun the relevant workflow. Do not change unrelated code or dependencies.

### Visual regression check
> Compare these before/after screenshots of the same Dislauncher screen. List only observable differences, classify intended versus unintended changes, and identify likely source files. Do not edit code until I approve the fix.

## Checkpoint template

Keep this record in the conversation or update the project status document:

- Current version:
- Last known-good commit:
- Last successful Actions run:
- APK artifact/release:
- Task completed:
- Device and scenarios tested:
- Known bugs:
- Current task:
- Next single task:
- Rollback commit, if needed:

## Definition of done

A task is done only when the agreed change is present in the repository, the relevant workflow result is known, the APK has been tested for the affected behavior, no unintended regression is observed, and the checkpoint is updated. If device testing has not happened, label it **build verified; device behavior unverified**.
