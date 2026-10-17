package com.insomdroid.dislauncher

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

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

        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
                    dynamicDarkColorScheme(this@BuiltInToolActivity)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    dynamicLightColorScheme(this@BuiltInToolActivity)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                BuiltInToolScreen(tool = tool, onBack = { finish() })
            }
        }
    }
}
