package com.insomdroid.dislauncher

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    var focusReturnCount by mutableIntStateOf(0)
        private set
    private var windowPreviouslyLostFocus = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent { DislauncherRoot(returnAnimationTrigger = focusReturnCount) }
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarPreference()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyStatusBarPreference()
            if (windowPreviouslyLostFocus) {
                windowPreviouslyLostFocus = false
                focusReturnCount++
            }
        } else {
            windowPreviouslyLostFocus = true
        }
    }

    private fun applyStatusBarPreference() {
        val hidden = getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
            .getBoolean("hide_status_bar", false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}

