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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    if (iconShape == "original") {
        val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
            initialValue = null, context, app.packageName, app.activityName, iconPack
        ) {
            value = withContext(Dispatchers.IO) {
                loadCachedAppIconBitmap(context, app, iconPack)?.asImageBitmap()
            }
        }
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.30f))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(size * 0.10f),
            contentAlignment = Alignment.Center
        ) {
            val loadedImage = image
            if (loadedImage != null) {
                Image(bitmap = loadedImage, contentDescription = app.label, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        val iconColor by produceState(
            initialValue = androidx.compose.ui.graphics.Color(0xFF6488B8), context, app.packageName
        ) {
            value = withContext(Dispatchers.IO) { dominantAppIconColor(context, app.packageName) }
        }
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val side = this.size.minDimension
                val iconSize = side * 0.44f
                val rectWidth = side * 0.56f
                val rectHeight = side * 0.31f
                when (iconShape) {
                    "circle" -> drawCircle(color = iconColor, radius = iconSize / 2f)
                    "outline_square" -> drawRect(
                        color = iconColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            (this.size.width - iconSize) / 2f, (this.size.height - iconSize) / 2f
                        ),
                        size = androidx.compose.ui.geometry.Size(iconSize, iconSize),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = (iconSize * 0.075f).coerceAtLeast(1.dp.toPx())
                        )
                    )
                    "filled_square" -> drawRect(
                        color = iconColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            (this.size.width - iconSize) / 2f, (this.size.height - iconSize) / 2f
                        ),
                        size = androidx.compose.ui.geometry.Size(iconSize, iconSize)
                    )
                    "filled_rectangle" -> drawRect(
                        color = iconColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            (this.size.width - rectWidth) / 2f, (this.size.height - rectHeight) / 2f
                        ),
                        size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight)
                    )
                }
            }
        }
    }
}


private val fallbackIconColor = androidx.compose.ui.graphics.Color(0xFF6488B8)

// Shared bounded LRU map for caches with different data and capacity.
private class BoundedLruCache<K, V>(private val maxSize: Int) :
    LinkedHashMap<K, V>(maxSize, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
        size > maxSize
}

// Bound the cache so installed apps or icon packs changing over time cannot grow it indefinitely.
private val dominantColorCache =
    BoundedLruCache<String, androidx.compose.ui.graphics.Color>(256)

private fun dominantAppIconColor(context: Context, packageName: String): androidx.compose.ui.graphics.Color {
    synchronized(dominantColorCache) {
        dominantColorCache[packageName]?.let { return it }
    }

    val bitmap = runCatching {
        drawableToBitmap(context.packageManager.getApplicationIcon(packageName))
    }.getOrNull() ?: return fallbackIconColor

    val buckets = HashMap<Int, Int>()
    val hsv = FloatArray(3)
    var sampled = 0
    var saturationTotal = 0f
    val stepX = (bitmap.width / 32).coerceAtLeast(1)
    val stepY = (bitmap.height / 32).coerceAtLeast(1)
    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 160) continue
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            android.graphics.Color.RGBToHSV(red, green, blue, hsv)
            if (hsv[2] < 0.16f) continue
            saturationTotal += hsv[1]
            sampled++
            val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
            buckets[key] = (buckets[key] ?: 0) + 1
        }
    }
    val result = if (sampled == 0 || saturationTotal / sampled < 0.12f) {
        fallbackIconColor
    } else {
        val dominant = buckets.maxByOrNull { it.value }?.key
        if (dominant == null) {
            fallbackIconColor
        } else {
            var red = (((dominant shr 8) and 0xF) * 16 + 8).coerceIn(0, 255)
            var green = (((dominant shr 4) and 0xF) * 16 + 8).coerceIn(0, 255)
            var blue = ((dominant and 0xF) * 16 + 8).coerceIn(0, 255)
            val brightness = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
            if (brightness > 0.70f) {
                val darkening = 0.70f / brightness
                red = (red * darkening).toInt()
                green = (green * darkening).toInt()
                blue = (blue * darkening).toInt()
            }
            androidx.compose.ui.graphics.Color(android.graphics.Color.rgb(red, green, blue))
        }
    }
    synchronized(dominantColorCache) {
        dominantColorCache[packageName] = result
    }
    return result
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
private const val APP_ICON_CACHE_BYTES = 4 * 1024 * 1024

private val appIconBitmapCache = object : LruCache<String, Bitmap>(APP_ICON_CACHE_BYTES) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
}

private fun loadCachedAppIconBitmap(
    context: Context,
    app: LauncherApp,
    iconPack: String
): Bitmap? {
    val cacheKey = "${app.packageName}/${app.activityName}|$iconPack"
    appIconBitmapCache.get(cacheKey)?.let { return it }

    val bitmap = drawableToBitmap(resolveAppDrawable(context, app, iconPack)) ?: return null
    synchronized(appIconBitmapCache) {
        if (appIconBitmapCache.get(cacheKey) == null) {
            appIconBitmapCache.put(cacheKey, bitmap)
        }
    }
    return bitmap
}

internal fun invalidateAppIconCaches(packageName: String?) {
    if (packageName.isNullOrBlank()) {
        appIconBitmapCache.evictAll()
        synchronized(dominantColorCache) { dominantColorCache.clear() }
        synchronized(iconPackMappings) { iconPackMappings.clear() }
        return
    }

    synchronized(appIconBitmapCache) {
        val keys = appIconBitmapCache.snapshot().keys.filter {
            it.startsWith("$packageName/") || it.endsWith("|$packageName")
        }
        keys.forEach { appIconBitmapCache.remove(it) }
    }
    synchronized(dominantColorCache) { dominantColorCache.remove(packageName) }
    synchronized(iconPackMappings) { iconPackMappings.remove(packageName) }
}

private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
    drawable ?: return null
    if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val width = drawable.intrinsicWidth.coerceIn(1, 128)
    val height = drawable.intrinsicHeight.coerceIn(1, 128)
    return runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()
}
