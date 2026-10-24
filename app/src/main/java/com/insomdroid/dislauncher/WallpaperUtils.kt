package com.insomdroid.dislauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

private const val MAX_CUSTOM_WALLPAPER_BYTES = 10L * 1024L * 1024L
private const val MAX_CUSTOM_WALLPAPER_SOURCE_PIXELS = 250_000_000L
private const val MAX_CUSTOM_WALLPAPER_DECODED_PIXELS = 3_000_000L

/**
 * Estimate the brightness at the edge covered by a transparent system bar.
 * Sampling is deliberately bounded so large wallpapers do not add noticeable work.
 */
internal fun wallpaperEdgeUsesDarkIcons(
    image: androidx.compose.ui.graphics.ImageBitmap,
    topEdge: Boolean
): Boolean? = runCatching {
    val bitmap = image.asAndroidBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) return@runCatching null
    val y = if (topEdge) (bitmap.height * 0.03f).toInt() else (bitmap.height * 0.97f).toInt()
    val step = (bitmap.width / 32).coerceAtLeast(1)
    var luminanceTotal = 0.0
    var samples = 0
    for (x in 0 until bitmap.width step step) {
        luminanceTotal += android.graphics.Color.luminance(bitmap.getPixel(x, y))
        samples++
    }
    samples > 0 && luminanceTotal / samples > 0.45
}.getOrNull()

/**
 * Estimate overall wallpaper brightness with a bounded sample for automatic foreground contrast.
 * Returns true for a generally light image, so the launcher can use dark text.
 */
internal fun wallpaperAverageIsLight(
    image: androidx.compose.ui.graphics.ImageBitmap
): Boolean? = runCatching {
    val bitmap = image.asAndroidBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) return@runCatching null
    val step = maxOf((bitmap.width / 32).coerceAtLeast(1), (bitmap.height / 32).coerceAtLeast(1))
    var luminanceTotal = 0.0
    var samples = 0
    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            luminanceTotal += android.graphics.Color.luminance(bitmap.getPixel(x, y))
            samples++
        }
    }
    if (samples > 0) luminanceTotal / samples > 0.45 else null
}.getOrNull()

/**
 * Keep a private copy of a selected wallpaper so it remains readable after reboot,
 * even when the document provider does not retain its URI permission.
 */
internal fun copyWallpaperToInternalStorage(context: Context, sourceUri: Uri): Uri? {
    val directory = File(context.filesDir, "wallpapers")
    if (!directory.exists() && !directory.mkdirs()) return null
    val temporaryFile = runCatching { File.createTempFile("wallpaper-", ".tmp", directory) }
        .getOrNull() ?: return null

    try {
        val input = context.contentResolver.openInputStream(sourceUri) ?: return null
        var totalBytes = 0L
        input.use { source ->
            temporaryFile.outputStream().buffered().use { destination ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_CUSTOM_WALLPAPER_BYTES) return null
                    destination.write(buffer, 0, count)
                }
            }
        }
        if (totalBytes <= 0L) return null

        // Validate that this is a decodable image before replacing the active selection.
        if (decodeWallpaperForScreen(context, Uri.fromFile(temporaryFile)) == null) return null
        val target = File(directory, "custom-wallpaper-${System.currentTimeMillis()}.img")
        if (!temporaryFile.renameTo(target)) return null
        return Uri.fromFile(target)
    } catch (_: Exception) {
        return null
    } finally {
        runCatching { temporaryFile.delete() }
    }
}

internal fun deleteOldInternalWallpapers(context: Context, keepUri: Uri?) {
    val directory = File(context.filesDir, "wallpapers")
    val keepFile = keepUri?.takeIf { it.scheme == "file" }?.path?.let(::File)
    directory.listFiles()
        ?.filter { it.name.startsWith("custom-wallpaper-") && it != keepFile }
        ?.forEach { runCatching { it.delete() } }
}

internal fun decodeWallpaperForScreen(
    context: Context,
    uri: Uri
): androidx.compose.ui.graphics.ImageBitmap? {
    val fileLength = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()
    if (fileLength != null && fileLength > MAX_CUSTOM_WALLPAPER_BYTES) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { input -> BitmapFactory.decodeStream(input, null, bounds) }

    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    if (width.toLong() * height.toLong() > MAX_CUSTOM_WALLPAPER_SOURCE_PIXELS) return null

    val metrics = context.resources.displayMetrics
    val targetWidth = metrics.widthPixels.coerceAtLeast(1)
    val targetHeight = metrics.heightPixels.coerceAtLeast(1)
    var sampleSize = 1
    while (
        (width.toLong() / sampleSize) * (height.toLong() / sampleSize) >
            MAX_CUSTOM_WALLPAPER_DECODED_PIXELS ||
        width / sampleSize > targetWidth * 2 ||
        height / sampleSize > targetHeight * 2
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return null
    return bitmap.asImageBitmap()
}
