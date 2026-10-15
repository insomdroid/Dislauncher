package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap
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
        resources.getIdentifier("appfilter", "xml", packageName) != 0
    }.getOrDefault(false)
}

internal fun findIconPacks(context: Context): List<IconPackInfo> {
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

    return candidates.asSequence()
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
}

@Composable
internal fun AppIcon(app: LauncherApp, iconPack: String, size: Dp = 20.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        context,
        app.packageName,
        app.activityName,
        iconPack
    ) {
        value = withContext(Dispatchers.IO) {
            drawableToBitmap(resolveAppDrawable(context, app, iconPack))?.asImageBitmap()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(size * 0.10f),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
// appfilter.xml can contain thousands of entries. Parse it once per pack.
private val iconPackMappings = ConcurrentHashMap<String, Map<String, String>>()

private fun loadIconPackMappings(context: Context, packageName: String): Map<String, String> {
    return iconPackMappings.computeIfAbsent(packageName) {
        runCatching {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
            if (xmlId == 0) return@runCatching emptyMap()
            val mappings = HashMap<String, String>()
            val parser = resources.getXml(xmlId)
            try {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        val key = componentKey(parser.getAttributeValue(null, "component"))
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (key != null && !drawable.isNullOrBlank()) mappings.putIfAbsent(key, drawable)
                    }
                    event = parser.next()
                }
            } finally {
                parser.close()
            }
            mappings
        }.getOrDefault(emptyMap())
    }
}

private fun resolveAppDrawable(context: Context, app: LauncherApp, iconPack: String): Drawable? {
    val pm = context.packageManager
    if (iconPack != "system") {
        val packed = runCatching {
            val drawableName = loadIconPackMappings(context, iconPack)[appKey(app)]
            val resources = pm.getResourcesForApplication(iconPack)
            val drawableId = drawableName?.let { resources.getIdentifier(it, "drawable", iconPack) } ?: 0
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
