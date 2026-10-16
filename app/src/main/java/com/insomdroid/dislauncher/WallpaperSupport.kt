package com.insomdroid.dislauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri

internal data class WallpaperColorOption(val key: String, val label: String, val argb: Int)

internal val WALLPAPER_COLOR_OPTIONS = listOf(
    WallpaperColorOption("black", "Black", Color.rgb(16, 16, 16)),
    WallpaperColorOption("white", "White", Color.WHITE),
    WallpaperColorOption("slate", "Slate", Color.rgb(75, 85, 99)),
    WallpaperColorOption("navy", "Navy", Color.rgb(22, 45, 78)),
    WallpaperColorOption("forest", "Forest", Color.rgb(37, 78, 57)),
    WallpaperColorOption("plum", "Plum", Color.rgb(94, 54, 91)),
    WallpaperColorOption("sand", "Sand", Color.rgb(218, 202, 170)),
    WallpaperColorOption("rose", "Rose", Color.rgb(190, 103, 121))
)

internal fun wallpaperColorInt(key: String): Int =
    WALLPAPER_COLOR_OPTIONS.firstOrNull { it.key == key }?.argb ?: WALLPAPER_COLOR_OPTIONS.first().argb

internal fun loadWallpaperBitmap(context: Context, mode: String, uri: String?): Bitmap? {
    val manager = WallpaperManager.getInstance(context)
    if (mode == "image") {
        val imageUri = uri?.let(Uri::parse) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { input -> BitmapFactory.decodeStream(input) }
        }.getOrNull()
    }
    if (mode != "system") return null
    val fromFile = runCatching {
        manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, BitmapFactory.Options().apply { inSampleSize = 2 })
        }
    }.getOrNull()
    if (fromFile != null) return fromFile
    return runCatching {
        val drawable = manager.drawable ?: return@runCatching null
        val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 720
        val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1280
        val scale = minOf(1f, 1440f / maxOf(sourceWidth, sourceHeight))
        val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()
}

internal fun applySelectedWallpaper(context: Context, mode: String, uri: String?, colorKey: String, target: String): Boolean {
    if (mode == "system") return true
    val which = when (target) {
        "lock" -> WallpaperManager.FLAG_LOCK
        "both" -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        else -> WallpaperManager.FLAG_SYSTEM
    }
    val manager = WallpaperManager.getInstance(context)
    return runCatching {
        when (mode) {
            "image" -> {
                val imageUri = uri?.let(Uri::parse) ?: return@runCatching false
                val input = context.contentResolver.openInputStream(imageUri) ?: return@runCatching false
                input.use { manager.setStream(it, null, true, which) }
                true
            }
            "solid" -> {
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(wallpaperColorInt(colorKey))
                    manager.setBitmap(bitmap, null, true, which)
                    true
                } finally { bitmap.recycle() }
            }
            else -> false
        }
    }.getOrDefault(false)
}
