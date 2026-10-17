# Dislauncher Compose performance audit

**Date:** 2026-10-17  
**Reviewed commit:** `127b5ce8cce53b3e8f447b4465563d0b81de6318`  
**Scope:** Home, app drawer, pager lifecycle, app-icon loading/cache, and custom wallpaper loading.

## Findings confirmed by source inspection

1. **Custom wallpaper was re-decoded on each Activity start.** `wallpaperImage` used both the saved URI and `launcherStarted` as `produceState` keys, then reset the bitmap to null. Moving between the launcher and another app therefore canceled/restarted decoding for the same wallpaper. The load is already moved to `Dispatchers.IO`, but repeating file decoding and clearing the current image is unnecessary. The state is now keyed by the wallpaper URI only.
2. **Animated alphabet-rail offsets read animation state during composition.** Each of the 27 letter items read `animatedOffset` in the value overload of `Modifier.offset`, and the popup read `animatedProgress` similarly. The dynamic reads now use the layout-phase lambda overload, following the official Compose performance guidance.
3. **App drawer LazyColumn items lacked explicit content types.** Stable keys already existed for the empty-state row, built-in-tools row, and app rows; explicit `contentType` declarations were added to let LazyColumn reuse compositions only among like item types.

## Checks that did not justify speculative changes

- App icon resolution, icon-pack parsing, and dominant-color sampling are dispatched to `Dispatchers.IO`; bounded bitmap/color/mapping caches are already present. No synchronous main-thread icon decoding was confirmed in the audited paths.
- Home's main vertical list is a normal `Column`/scroll container with a small bounded favorite-app collection, so converting it to a lazy list would not be justified by the guidance.
- The pager uses `beyondViewportPageCount = 1`, so it intentionally keeps both pages composed. This may trade background work and memory for a more immediate swipe. It was left unchanged because a source-only review cannot establish whether changing it would improve the observed drawer-open latency without making swipes worse on the target phone.

## Verification limits

This is a source-based audit, not a device trace. The repository was built with GitHub Actions after the changes; that verifies compilation, release shrinking, APK creation, and artifact upload, but it does not measure frame time or prove the reported pause is eliminated. A before/after Perfetto/Macrobenchmark trace on the target device would be needed to attribute the remaining pause confidently.

## Android Skills references

- [Compose performance skill](https://github.com/android/skills/tree/main/jetpack-compose/jetpack-compose-performance)
- [Lazy layouts](https://github.com/android/skills/blob/main/jetpack-compose/jetpack-compose-performance/references/lazy-layouts.md)
- [Animation phase deferral](https://github.com/android/skills/blob/main/jetpack-compose/jetpack-compose-performance/references/animation.md)
- [Layout performance](https://github.com/android/skills/blob/main/jetpack-compose/jetpack-compose-performance/references/layouts.md)
- [Effects and threading](https://github.com/android/skills/blob/main/jetpack-compose/jetpack-compose-performance/references/effects-and-threading.md)
