package com.insomdroid.dislauncher

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalDensity

internal class BuiltInToolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val tool = intent.getStringExtra(EXTRA_BUILT_IN_TOOL)
            ?.let { name -> runCatching { BuiltInTool.valueOf(name) }.getOrNull() }
        if (tool == null) {
            finish()
            return
        }

        val preferences = LauncherPreferences.load(
            getSharedPreferences("dislauncher_preferences", MODE_PRIVATE)
        )
        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDark = when (preferences.themeMode) {
                "light" -> false
                "dark", "black" -> true
                else -> systemDark
            }
            DislauncherTheme(
                preferences = preferences,
                isDark = isDark,
                baseDensity = LocalDensity.current
            ) {
                BuiltInToolScreen(tool = tool, onBack = { finish() })
            }
        }
    }
}
