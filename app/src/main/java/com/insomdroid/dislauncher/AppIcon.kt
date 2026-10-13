package com.insomdroid.dislauncher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.xmlpull.v1.XmlPullParser

internal data class IconPackInfo(val packageName: String, val label: String)

internal fun findIconPacks(context: Context): List<IconPackInfo> {
    val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.dlto.atom.launcher.THEME"
    )
    val pm = context.packageManager
    return actions.flatMap { action ->
        runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(android.content.Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
    }.mapNotNull { result ->
        val activity = result.activityInfo ?: return@mapNotNull null
        val pkg = activity.packageName
        if (pkg == context.packageName) return@mapNotNull null
        val label = runCatching { result.loadLabel(pm).toString() }.getOrDefault(pkg)
        IconPackInfo(pkg, label)
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}

@Composable
internal fun AppIcon(app: LauncherApp, iconPack: String, size: Dp = 20.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image = remember(context, app.packageName, app.activityName, iconPack) {
        drawableToBitmap(resolveAppDrawable(context, app, iconPack))?.asImageBitmap()
    }
    if (image != null) {
        Image(bitmap = image, contentDescription = app.label, modifier = Modifier.size(size))
    }
}

private fun resolveAppDrawable(context: Context, app: LauncherApp, iconPack: String): Drawable? {
    val pm = context.packageManager
    if (iconPack != "system") {
        val packed = runCatching {
            val resources = pm.getResourcesForApplication(iconPack)
            val xmlId = resources.getIdentifier("appfilter", "xml", iconPack)
            if (xmlId == 0) null else {
                val parser = resources.getXml(xmlId)
                var drawableName: String? = null
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && drawableName == null) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (componentMatches(component, app) && !drawable.isNullOrBlank()) drawableName = drawable
                    }
                    event = parser.next()
                }
                parser.close()
                val drawableId = drawableName?.let { resources.getIdentifier(it, "drawable", iconPack) } ?: 0
                if (drawableId != 0) resources.getDrawable(drawableId, context.theme) else null
            }
        }.getOrNull()
        if (packed != null) return packed
    }
    return runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
}

private fun componentMatches(component: String?, app: LauncherApp): Boolean {
    if (component.isNullOrBlank()) return false
    val body = component.removePrefix("ComponentInfo{").removeSuffix("}")
    val parts = body.split("/", limit = 2)
    if (parts.size != 2 || parts[0] != app.packageName) return false
    val className = when {
        parts[1].startsWith(".") -> parts[0] + parts[1]
        !parts[1].contains(".") -> parts[0] + "." + parts[1]
        else -> parts[1]
    }
    return className == app.activityName
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
