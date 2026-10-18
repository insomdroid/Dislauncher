package com.insomdroid.dislauncher

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver as AndroidBroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray

private const val REMINDER_CHANNEL_ID = "dislauncher_local_reminders"
private const val EXTRA_REMINDER_ID = "reminder_id"
private const val EXTRA_REMINDER_TITLE = "reminder_title"

internal fun scheduleLocalReminder(context: Context, id: String, title: String, atMillis: Long) {
    if (atMillis <= System.currentTimeMillis()) return
    val intent = Intent(context, LocalReminderReceiver::class.java)
        .putExtra(EXTRA_REMINDER_ID, id)
        .putExtra(EXTRA_REMINDER_TITLE, title)
    val pending = PendingIntent.getBroadcast(
        context, id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
}

internal fun cancelLocalReminder(context: Context, id: String) {
    val intent = Intent(context, LocalReminderReceiver::class.java)
        .putExtra(EXTRA_REMINDER_ID, id)
    val pending = PendingIntent.getBroadcast(
        context, id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    pending.cancel()
}

class LocalReminderReceiver : AndroidBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE).orEmpty().ifBlank { "Reminder" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "Dislauncher reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Local reminders created in Dislauncher Calendar" }
            )
        }
        val openApp = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder")
            .setContentText(title)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(id.hashCode(), notification)
    }
}

class ReminderBootReceiver : AndroidBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("dislauncher_local_tasks", Context.MODE_PRIVATE)
        runCatching {
            val array = JSONArray(prefs.getString("tasks_json", "[]") ?: "[]")
            for (i in 0 until array.length()) {
                val task = array.optJSONObject(i) ?: continue
                if (task.optString("type") != "reminder" || task.optBoolean("completed")) continue
                val id = task.optString("id")
                val title = task.optString("title")
                val atMillis = task.optLong("reminderAt")
                if (id.isNotBlank() && atMillis > System.currentTimeMillis()) {
                    scheduleLocalReminder(context, id, title, atMillis)
                }
            }
        }
    }
}
