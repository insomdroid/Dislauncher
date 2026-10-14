package com.insomdroid.dislauncher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

internal class DislauncherAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var activeService: DislauncherAccessibilityService? = null

        fun expandNotifications(): Boolean {
            val service = activeService ?: return false
            return runCatching {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }.getOrDefault(false)
        }
    }
}
