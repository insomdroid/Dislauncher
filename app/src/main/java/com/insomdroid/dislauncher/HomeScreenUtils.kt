package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class BatteryState(
    val percent: Int?,
    val isCharging: Boolean
)

internal fun readBatteryState(context: Context): BatteryState {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra("level", -1) ?: -1
    val scale = intent?.getIntExtra("scale", -1) ?: -1
    val percent = if (level >= 0 && scale > 0) {
        (level * 100 / scale).coerceIn(0, 100)
    } else {
        null
    }
    val isCharging = intent?.getIntExtra("plugged", 0)?.let { it != 0 } ?: false
    return BatteryState(percent, isCharging)
}

internal fun formatHomeDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    if (locale.language != "en") {
        return date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    }
    val day = date.dayOfMonth
    val suffix = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return date.format(DateTimeFormatter.ofPattern("EEEE, ", locale)) +
        day + suffix + date.format(DateTimeFormatter.ofPattern(" MMMM", locale))
}
