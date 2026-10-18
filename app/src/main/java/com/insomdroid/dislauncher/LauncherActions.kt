package com.insomdroid.dislauncher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import org.json.JSONObject

internal const val MAX_CUSTOM_FONT_BYTES = 5 * 1024 * 1024

internal fun importUserCustomFont(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val displayName = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment.orEmpty()

    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    require(extension == "ttf" || extension == "otf") {
        "Only .ttf and .otf font files are supported."
    }
    val fontDirectory = File(context.filesDir, "fonts")
    if (!fontDirectory.isDirectory && !fontDirectory.mkdirs()) {
        throw IOException("Unable to create private font storage.")
    }
    val temporaryFile = File(fontDirectory, ".custom-font.$extension.tmp")
    try {
        val input = resolver.openInputStream(uri) ?: throw IOException("Unable to read selected font.")
        var totalBytes = 0L
        val header = ByteArray(4)
        var headerCount = 0
        input.use { source ->
            FileOutputStream(temporaryFile).use { destination ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (totalBytes + count > MAX_CUSTOM_FONT_BYTES.toLong()) {
                        throw IllegalArgumentException("Font files must be 5 MB or smaller.")
                    }
                    val headerPart = minOf(count, header.size - headerCount)
                    if (headerPart > 0) {
                        System.arraycopy(buffer, 0, header, headerCount, headerPart)
                        headerCount += headerPart
                    }
                    destination.write(buffer, 0, count)
                    totalBytes += count
                }
                destination.fd.sync()
            }
        }
        require(totalBytes >= 12L) { "The selected font file is too small." }
        val signature = String(header, Charsets.ISO_8859_1)
        val validHeader = when (extension) {
            "ttf" -> header.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                signature == "true" || signature == "typ1"
            "otf" -> signature == "OTTO"
            else -> false
        }
        require(validHeader) { "The file extension does not match a supported font format." }
        runCatching { Typeface.createFromFile(temporaryFile) }.getOrElse {
            throw IllegalArgumentException("Android could not load this font file.", it)
        }
        val targetFile = File(fontDirectory, "custom-font.$extension")
        Files.move(temporaryFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val otherExtension = if (extension == "ttf") "otf" else "ttf"
        File(fontDirectory, "custom-font.$otherExtension").delete()
        val safeName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot { Character.isISOControl(it) }
            .trim()
            .take(80)
        return safeName.ifBlank { "Custom font.$extension" }
    } catch (error: Exception) {
        temporaryFile.delete()
        throw error
    }
}

internal fun readAppNameOverrides(prefs: android.content.SharedPreferences): Map<String, String> {
    val encoded = prefs.getString("renamed_app_labels_json", null).orEmpty()
    if (encoded.isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(encoded)
        json.keys().asSequence().mapNotNull { key ->
            val value = json.optString(key).trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }.toMap()
    }.getOrDefault(emptyMap())
}

internal fun loadLauncherApps(context: Context): List<LauncherApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val results = pm.queryIntentActivities(intent, 0)
    val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    return results.mapNotNull { resolveInfo ->
        val activity = resolveInfo.activityInfo ?: return@mapNotNull null
        val pkg = activity.packageName
        if (pkg == context.packageName) return@mapNotNull null
        val info = activity.applicationInfo ?: return@mapNotNull null
        LauncherApp(
            packageName = pkg,
            activityName = activity.name,
            label = resolveInfo.loadLabel(pm).toString().ifBlank { pkg },
            isSystem = (info.flags and systemFlags) != 0
        )
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

internal fun launchApp(context: Context, app: LauncherApp) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

internal fun openClock(context: Context) {
    val packageManager = context.packageManager
    val candidates = listOf(
        Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK"),
        Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
        Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
    )
    for (candidate in candidates) {
        val canResolve = runCatching { candidate.resolveActivity(packageManager) != null }.getOrDefault(false)
        if (!canResolve) continue
        val launched = runCatching {
            context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (launched) return
    }
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val clockApp = runCatching {
        packageManager.queryIntentActivities(launcherIntent, 0).firstOrNull { resolved ->
            val label = resolved.loadLabel(packageManager).toString().lowercase(Locale.getDefault())
            val packageName = resolved.activityInfo?.packageName.orEmpty().lowercase(Locale.getDefault())
            label.contains("clock") || label.contains("alarm") ||
                packageName.contains("deskclock") || packageName.contains("clock")
        }
    }.getOrNull()
    val activityInfo = clockApp?.activityInfo ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(activityInfo.packageName, activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun openCalendar(context: Context) {
    val appIntent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_APP_CALENDAR)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val viewIntent = Intent(
        Intent.ACTION_VIEW,
        android.provider.CalendarContract.CONTENT_URI
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    listOf(appIntent, viewIntent).firstOrNull {
        it.resolveActivity(context.packageManager) != null
    }?.let { runCatching { context.startActivity(it) } }
}

internal fun openMusicPlayer(context: Context, apps: List<LauncherApp>) {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
    if (intent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(intent) }
        return
    }
    val keywords = listOf("spotify", "youtube music", "music", "soundcloud", "deezer", "vlc", "poweramp", "podcast")
    val candidate = apps.firstOrNull { app ->
        val label = app.label.lowercase(Locale.getDefault())
        val pkg = app.packageName.lowercase(Locale.getDefault())
        keywords.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
    }
    if (candidate != null) launchApp(context, candidate)
    else runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
}

internal fun lockDevice(
    context: Context,
    adminComponent: ComponentName,
    manager: DevicePolicyManager
) {
    if (runCatching { manager.isAdminActive(adminComponent) }.getOrDefault(false)) {
        runCatching { manager.lockNow() }
    } else {
        runCatching {
            context.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            )
        }
    }
}

@android.annotation.SuppressLint("WrongConstant", "PrivateApi")
internal fun expandNotificationPanel(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
            ?: throw IllegalStateException("Android status bar service is unavailable")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(statusBarService)
    } catch (error: Exception) {
        android.util.Log.w(
            "Dislauncher",
            "Cannot expand notification shade on this device",
            error
        )
    }
}

