package com.insomdroid.dislauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateMapOf

/**
 * Tracks currently active Android notifications by the package that posted them.
 *
 * The state map is observable by Compose, so changes from the notification listener
 * automatically refresh the notification badges on the home screen.
 */
internal object NotificationDotState {
    private val notificationCounts = mutableStateMapOf<String, Int>()

    @Volatile
    private var listener: DislauncherNotificationListener? = null

    val activePackages: Set<String>
        get() = notificationCounts.keys.toSet()

    fun countFor(packageName: String): Int = notificationCounts[packageName] ?: 0

    /** Refresh the count when notification-dot settings change. */
    fun refresh() {
        listener?.refreshActiveNotifications()
    }

    internal fun onListenerConnected(service: DislauncherNotificationListener) {
        listener = service
        service.refreshActiveNotifications()
    }

    internal fun onListenerDisconnected(service: DislauncherNotificationListener) {
        if (listener === service) {
            listener = null
        }
    }

    internal fun update(notifications: Array<StatusBarNotification>?) {
        val nextCounts = notifications
            .orEmpty()
            .asSequence()
            .map(StatusBarNotification::getPackageName)
            .groupingBy { it }
            .eachCount()

        notificationCounts.clear()
        notificationCounts.putAll(nextCounts)
    }
}

/**
 * Android calls this service whenever the active notification list changes.
 * Notification access must be enabled by the user in Android system settings.
 */
internal class DislauncherNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationDotState.onListenerConnected(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        NotificationDotState.onListenerDisconnected(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        NotificationDotState.onListenerDisconnected(this)
        super.onDestroy()
    }

    internal fun refreshActiveNotifications() {
        NotificationDotState.update(activeNotifications)
    }
}
