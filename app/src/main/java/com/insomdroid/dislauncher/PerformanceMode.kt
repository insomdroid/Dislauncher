package com.insomdroid.dislauncher

/**
 * Process-wide switch for the low-end device mode. It is mirrored from
 * [LauncherPreferences.lowEndMode] by LauncherRoot so non-composable code
 * (icon rendering, wallpaper decoding) can pick cheaper settings.
 */
internal object PerformanceMode {
    @Volatile
    var lowEnd: Boolean = true

    /** Longest edge, in pixels, of cached app-icon bitmaps. */
    val iconEdge: Int get() = if (lowEnd) 128 else 192

    /** Memory budget of the app-icon bitmap cache. */
    val iconCacheBytes: Int get() = if (lowEnd) 4 * 1024 * 1024 else 8 * 1024 * 1024

    /** Wallpapers are decoded at no more than this many pixels. */
    val wallpaperMaxPixels: Long get() = if (lowEnd) 1_500_000L else 3_000_000L

    /** Wallpapers are never decoded larger than screen size times this factor. */
    val wallpaperScreenFactor: Int get() = if (lowEnd) 1 else 2
}
