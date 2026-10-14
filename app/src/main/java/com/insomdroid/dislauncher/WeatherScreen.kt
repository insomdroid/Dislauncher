package com.insomdroid.dislauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WeatherScreen(weatherUnit: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var locationGranted by remember { mutableStateOf(hasWeatherLocationPermission(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    var status by remember { mutableStateOf("Location permission is required.") }
    var loading by remember { mutableStateOf(false) }
    val weatherAnimation = rememberInfiniteTransition(label = "weather-symbol")
    val symbolOffset by weatherAnimation.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "weather-symbol-offset"
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results.values.any { it } || hasWeatherLocationPermission(context)
        if (locationGranted) refreshKey++ else status = "Location permission was denied."
    }
    LaunchedEffect(weatherUnit, refreshKey, locationGranted) {
        if (!locationGranted) {
            weather = null
            loading = false
            status = "Allow location access to get current weather."
        } else {
            loading = true
            status = "Getting current weather…"
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, weatherUnit) }
            weather = fetched
            status = if (fetched == null) "Weather is unavailable. Check location and internet, then retry." else ""
            loading = false
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = primary, fontSize = 28.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 18.dp))
            Text("Weather", color = primary, fontSize = 18.sp)
        }
        Spacer(Modifier.height(32.dp))
        Text("CURRENT CONDITIONS", color = secondary, fontSize = 10.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(12.dp))
        if (weather != null) {
            Text(
                weatherSymbol(weather!!.condition),
                color = primary,
                fontSize = 52.sp,
                lineHeight = 58.sp,
                modifier = Modifier.offset(y = symbolOffset.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(weather!!.temperature, color = primary, fontSize = 60.sp, lineHeight = 64.sp,
                fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp)
            Text(weather!!.condition, color = primary, fontSize = 18.sp)
            weather!!.feelsLike?.let { Spacer(Modifier.height(5.dp)); Text("Feels like $it", color = secondary, fontSize = 12.sp) }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("HUMIDITY", color = secondary, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Text(weather!!.humidity?.let { "$it%" } ?: "—", color = primary, fontSize = 17.sp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("WIND", color = secondary, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Text(weather!!.wind ?: "—", color = primary, fontSize = 17.sp)
                }
            }
            if (weather!!.high != null || weather!!.low != null) {
                Spacer(Modifier.height(18.dp))
                Text(listOfNotNull(weather!!.high?.let { "H: $it" }, weather!!.low?.let { "L: $it" }).joinToString("     "),
                    color = secondary, fontSize = 12.sp)
            }
        } else Text(status, color = secondary, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(30.dp))
        Text(
            when { loading -> "UPDATING…"; !locationGranted -> "ALLOW LOCATION"; else -> "REFRESH" },
            color = primary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable {
                if (!locationGranted) permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                ) else refreshKey++
            }.padding(vertical = 8.dp)
        )
    }
}
private fun hasWeatherLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun weatherSymbol(condition: String): String = when {
    condition.contains("thunder", ignoreCase = true) -> "ϟ"
    condition.contains("rain", ignoreCase = true) || condition.contains("shower", ignoreCase = true) ||
        condition.contains("drizzle", ignoreCase = true) -> "☂"
    condition.contains("snow", ignoreCase = true) -> "❄"
    condition.contains("fog", ignoreCase = true) -> "≋"
    condition.contains("cloud", ignoreCase = true) -> "☁"
    condition.contains("clear", ignoreCase = true) -> "☼"
    else -> "◌"
}
