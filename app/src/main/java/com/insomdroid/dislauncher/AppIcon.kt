package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class IconPackInfo(val packageName: String, val label: String)

private val iconPackActions = listOf(
    "org.adw.launcher.THEMES",
    "com.novalauncher.THEME",
    "com.anddoes.launcher.THEME",
    "com.teslacoilsw.launcher.THEME",
    "com.gau.go.launcherex.theme",
    "com.dlto.atom.launcher.THEME",
    "com.smartlauncher.THEME",
    "com.actionlauncher.THEME",
    "com.dreamon.theme"
)

@Suppress("DEPRECATION")
private fun queryActivities(context: Context, intent: Intent): List<ResolveInfo> {
    return runCatching {
        context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
    }.getOrDefault(emptyList())
}

private fun hasAppFilter(context: Context, packageName: String): Boolean {
    return runCatching {
        val resources = context.packageManager.getResourcesForApplication(packageName)
        if (resources.getIdentifier("appfilter", "xml", packageName) != 0 ||
            resources.getIdentifier("appfilter", "raw", packageName) != 0
        ) {
            true
        } else {
            val packageContext = context.createPackageContext(packageName, 0)
            packageContext.assets.open("appfilter.xml").use { true }
        }
    }.getOrDefault(false)
}

private val iconPackDetectionLock = Any()
@Volatile
private var cachedIconPackList: List<IconPackInfo>? = null

internal fun invalidateIconPackDetectionCache() {
    synchronized(iconPackDetectionLock) {
        cachedIconPackList = null
    }
}

internal fun findIconPacks(context: Context): List<IconPackInfo> = synchronized(iconPackDetectionLock) {
    cachedIconPackList?.let { return@synchronized it }
    val pm = context.packageManager
    val candidates = linkedSetOf<String>()

    iconPackActions.forEach { action ->
        queryActivities(context, Intent(action)).forEach { result ->
            result.activityInfo?.packageName?.let(candidates::add)
        }
    }

    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    queryActivities(context, launcherIntent).forEach { result ->
        result.activityInfo?.packageName?.let(candidates::add)
    }

    val result = candidates.asSequence()
        .filter { it != context.packageName }
        .filter { hasAppFilter(context, it) }
        .mapNotNull { packageName ->
            runCatching {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                    .ifBlank { packageName }
                IconPackInfo(packageName, label)
            }.getOrNull()
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
    cachedIconPackList = result
    result
}

@Composable
internal fun AppIcon(
    app: LauncherApp,
    iconPack: String,
    size: Dp = 20.dp,
    iconShape: String = "original"
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Icon packs bring their own artwork, so the built-in shapes only apply to system icons.
    val shape = if (iconPack == "system") iconShape else "original"
    val loadKey = "${app.packageName}/${app.activityName}@${app.versionCode}|$shape|$iconPack"
    // A cache hit is shown on the very first frame, which avoids flicker while scrolling.
    var image by remember(loadKey) {
        mutableStateOf(peekCachedAppIconBitmap(app, iconPack, shape)?.asImageBitmap())
    }
    var failed by remember(loadKey) { mutableStateOf(false) }
    LaunchedEffect(loadKey) {
        if (image == null) {
            val bitmap = withContext(Dispatchers.IO) {
                loadCachedAppIconBitmap(context, app, iconPack, shape)
            }
            if (bitmap != null) image = bitmap.asImageBitmap() else failed = true
        }
    }
    // No background box: icons sit directly on the wallpaper, so nothing dark shows behind them.
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        val loadedImage = image
        if (loadedImage != null) {
            Image(
                bitmap = loadedImage,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize()
            )
        } else if (failed) {
            Text(
                app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Shared bounded LRU map for caches with different data and capacity.
private class BoundedLruCache<K, V>(private val maxSize: Int) :
    LinkedHashMap<K, V>(maxSize, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
        size > maxSize
}

// appfilter.xml can contain thousands of entries. Keep only a small LRU of parsed packs.
private val iconPackMappings = BoundedLruCache<String, Map<String, String>>(8)

private fun parseIconPackMappings(parser: XmlPullParser): Map<String, String> {
    val parsed = HashMap<String, String>()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "item") {
            val key = componentKey(parser.getAttributeValue(null, "component"))
            val drawable = parser.getAttributeValue(null, "drawable")
            if (key != null && !drawable.isNullOrBlank()) parsed.putIfAbsent(key, drawable)
        }
        event = parser.next()
    }
    return parsed
}

private fun loadIconPackMappings(context: Context, packageName: String): Map<String, String> {
    synchronized(iconPackMappings) {
        iconPackMappings[packageName]?.let { return it }
        val mappings = runCatching {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
            val rawId = resources.getIdentifier("appfilter", "raw", packageName)

            when {
                xmlId != 0 -> {
                    val parser = resources.getXml(xmlId)
                    try {
                        parseIconPackMappings(parser)
                    } finally {
                        parser.close()
                    }
                }
                rawId != 0 -> {
                    resources.openRawResource(rawId).use { stream ->
                        val parser = android.util.Xml.newPullParser()
                        parser.setInput(stream, "UTF-8")
                        parseIconPackMappings(parser)
                    }
                }
                else -> {
                    val packageContext = context.createPackageContext(packageName, 0)
                    packageContext.assets.open("appfilter.xml").use { stream ->
                        val parser = android.util.Xml.newPullParser()
                        parser.setInput(stream, "UTF-8")
                        parseIconPackMappings(parser)
                    }
                }
            }
        }.getOrDefault(emptyMap())
        iconPackMappings[packageName] = mappings
        return mappings
    }
}

private fun resolveAppDrawable(context: Context, app: LauncherApp, iconPack: String): Drawable? {
    val pm = context.packageManager
    if (iconPack != "system") {
        val packed = runCatching {
            val drawableName = loadIconPackMappings(context, iconPack)[appKey(app)]
            val resources = pm.getResourcesForApplication(iconPack)
            val drawableId = drawableName?.let { name ->
                resources.getIdentifier(name, "drawable", iconPack)
                    .takeIf { it != 0 }
                    ?: resources.getIdentifier(name, "mipmap", iconPack).takeIf { it != 0 }
            } ?: 0
            if (drawableId != 0) resources.getDrawable(drawableId, context.theme) else null
        }.getOrNull()
        if (packed != null) return packed
    }
    return runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
}

private fun componentKey(component: String?): String? {
    if (component.isNullOrBlank()) return null
    val body = component.removePrefix("ComponentInfo{").removeSuffix("}")
    val parts = body.split("/", limit = 2)
    if (parts.size != 2) return null
    val className = when {
        parts[1].startsWith(".") -> parts[0] + parts[1]
        !parts[1].contains(".") -> parts[0] + "." + parts[1]
        else -> parts[1]
    }
    return parts[0] + "/" + className
}

private fun appKey(app: LauncherApp): String = app.packageName + "/" + app.activityName

// Keep resolved icon bitmaps in a small memory cache so returning to the drawer
// does not repeatedly resolve drawables and convert them to bitmaps.
private val appIconBitmapCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
}

private fun iconCacheKey(app: LauncherApp, iconPack: String, shape: String): String =
    "${app.packageName}/${app.activityName}@${app.versionCode}|$shape|$iconPack"

internal fun peekCachedAppIconBitmap(app: LauncherApp, iconPack: String, shape: String): Bitmap? =
    appIconBitmapCache.get(iconCacheKey(app, iconPack, shape))

private fun loadCachedAppIconBitmap(
    context: Context,
    app: LauncherApp,
    iconPack: String,
    shape: String
): Bitmap? {
    val cacheKey = iconCacheKey(app, iconPack, shape)
    appIconBitmapCache.get(cacheKey)?.let { return it }

    val drawable = resolveAppDrawable(context, app, iconPack)
    val bitmap = (if (shape == "original") drawableToBitmap(drawable) else renderShapedIcon(drawable, shape))
        ?: return null
    synchronized(appIconBitmapCache) {
        val budget = PerformanceMode.iconCacheBytes
        if (appIconBitmapCache.maxSize() != budget) appIconBitmapCache.resize(budget)
        if (appIconBitmapCache.get(cacheKey) == null) {
            appIconBitmapCache.put(cacheKey, bitmap)
        }
    }
    return bitmap
}

internal fun invalidateAppIconCaches(packageName: String?) {
    if (packageName.isNullOrBlank()) {
        appIconBitmapCache.evictAll()
        synchronized(iconPackMappings) { iconPackMappings.clear() }
        return
    }

    synchronized(appIconBitmapCache) {
        val keys = appIconBitmapCache.snapshot().keys.filter {
            it.startsWith("$packageName/") || it.endsWith("|$packageName")
        }
        keys.forEach { appIconBitmapCache.remove(it) }
    }
    synchronized(iconPackMappings) { iconPackMappings.remove(packageName) }
}

/**
 * Draws an app icon so it completely fills a circle or rounded square.
 *
 * Adaptive icons are drawn from their own background and foreground layers (not the system
 * mask), so the artwork fills the whole shape instead of shrinking into it. Older, non-adaptive
 * icons are placed on a neutral backdrop that contrasts with them.
 */
private fun renderShapedIcon(drawable: Drawable?, shape: String): Bitmap? {
    drawable ?: return null
    val edge = PerformanceMode.iconEdge
    return runCatching {
        val content = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            // Layers are 108dp, of which the central 72dp is the visible area: extend by 25% per side.
            val inset = (edge * 0.25f).toInt()
            listOf(drawable.background, drawable.foreground).forEach { layer ->
                layer?.setBounds(-inset, -inset, edge + inset, edge + inset)
                layer?.draw(canvas)
            }
        } else {
            val glyph = drawableToBitmap(drawable) ?: return null
            canvas.drawColor(backdropColorFor(glyph))
            val pad = edge * 0.14f
            canvas.drawBitmap(
                glyph, null,
                android.graphics.RectF(pad, pad, edge - pad, edge - pad),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            )
        }

        // Mask with an anti-aliased path; a plain clipPath would leave jagged edges.
        val rect = android.graphics.RectF(0f, 0f, edge.toFloat(), edge.toFloat())
        val path = android.graphics.Path().apply {
            if (shape == "circle") addOval(rect, android.graphics.Path.Direction.CW)
            else addRoundRect(rect, edge * 0.22f, edge * 0.22f, android.graphics.Path.Direction.CW)
        }
        val result = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.BitmapShader(
                content, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP
            )
        }
        Canvas(result).drawPath(path, paint)
        content.recycle()
        result
    }.getOrNull()
}

/** Light backdrop for dark icons and dark backdrop for light icons, from a coarse luminance sample. */
private fun backdropColorFor(glyph: Bitmap): Int {
    var total = 0.0
    var samples = 0
    val stepX = (glyph.width / 16).coerceAtLeast(1)
    val stepY = (glyph.height / 16).coerceAtLeast(1)
    for (y in 0 until glyph.height step stepY) {
        for (x in 0 until glyph.width step stepX) {
            val pixel = glyph.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 160) continue
            total += android.graphics.Color.luminance(pixel)
            samples++
        }
    }
    val average = if (samples == 0) 0.5 else total / samples
    return if (average > 0.55) 0xFF2B2D31.toInt() else 0xFFF1F2F4.toInt()
}

private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
    drawable ?: return null

    if (drawable is android.graphics.drawable.BitmapDrawable) {
        val source = drawable.bitmap ?: return null
        val longestEdge = maxOf(source.width, source.height)
        if (longestEdge <= PerformanceMode.iconEdge) return source

        // Do not retain oversized package/icon-pack bitmaps in the drawer cache.
        // Create a smaller copy; never mutate or recycle the drawable's source bitmap.
        val scale = PerformanceMode.iconEdge.toFloat() / longestEdge
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return runCatching {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        }.getOrNull()
    }

    val intrinsicWidth = drawable.intrinsicWidth.coerceAtLeast(1)
    val intrinsicHeight = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = minOf(
        1f,
        PerformanceMode.iconEdge.toFloat() / intrinsicWidth,
        PerformanceMode.iconEdge.toFloat() / intrinsicHeight
    )
    val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
    val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
    return runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()
}
