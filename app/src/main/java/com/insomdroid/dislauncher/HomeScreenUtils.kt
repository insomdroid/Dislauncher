package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun readBatteryCharging(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    return intent.getIntExtra("plugged", 0) != 0
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
