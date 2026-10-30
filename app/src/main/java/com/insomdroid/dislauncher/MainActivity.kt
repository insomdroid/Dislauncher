package com.insomdroid.dislauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // Set a theme-appropriate launch background before Compose draws its first frame.
        // This avoids a black flash in Light mode and respects an explicitly selected solid wallpaper.
        val preferences = getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
        val themeMode = preferences.getString("theme_mode", "system") ?: "system"
        val wallpaperMode = preferences.getString("wallpaper_mode", "default") ?: "default"
        val systemNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val darkLaunchBackground = when (themeMode) {
            "light" -> false
            "dark", "black" -> true
            else -> systemNight
        }
        val launchBackgroundColor = if (wallpaperMode == "solid") {
            runCatching {
                Color.parseColor(preferences.getString("wallpaper_color", "#000000") ?: "#000000")
            }.getOrElse { if (darkLaunchBackground) Color.BLACK else Color.rgb(242, 242, 242) }
        } else if (darkLaunchBackground) {
            Color.rgb(17, 17, 17)
        } else {
            Color.rgb(242, 242, 242)
        }
        window.setBackgroundDrawable(ColorDrawable(launchBackgroundColor))
        setContent { DislauncherRoot() }
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarPreference()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarPreference()
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

