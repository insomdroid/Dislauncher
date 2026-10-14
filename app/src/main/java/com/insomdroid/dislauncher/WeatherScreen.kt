package com.insomdroid.dislauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WeatherScreen(
    weatherUnit: String,
    cachedWeather: WeatherInfo?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var locationGranted by remember { mutableStateOf(hasWeatherLocationPermission(context)) }
    var weather by remember { mutableStateOf(cachedWeather) }
    var status by remember { mutableStateOf("Location permission is required.") }
    var loading by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results.values.any { it } || hasWeatherLocationPermission(context)
        if (!locationGranted) status = "Location permission was denied."
    }

    LaunchedEffect(weatherUnit, locationGranted, cachedWeather) {
        if (cachedWeather != null) {
            weather = cachedWeather
            loading = false
            status = ""
        } else if (!locationGranted) {
            weather = null
            loading = false
            status = "Allow location access to see local weather."
        } else {
            loading = true
            status = "Getting weather…"
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, weatherUnit) }
            weather = fetched
            status = if (fetched == null) {
                "Weather is unavailable. Check location and internet."
            } else ""
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                color = primary,
                fontSize = 30.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 18.dp)
            )
            Text("Weather", color = primary, style = MaterialTheme.typography.titleLarge)
        }

        if (weather != null) {
            Spacer(Modifier.height(26.dp))
            Text(
                weather!!.locationName ?: "Local weather",
                color = secondary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(20.dp))
            WeatherIcon(weather!!.condition, primary, Modifier.size(124.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                weather!!.temperature,
                color = primary,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Text(
                weather!!.condition,
                color = primary,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            weather!!.feelsLike?.let {
                Spacer(Modifier.height(6.dp))
                Text("Feels like $it", color = secondary, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("HUMIDITY", color = secondary, style = MaterialTheme.typography.labelMedium)
                    Text(weather!!.humidity?.let { "$it%" } ?: "—",
                        color = primary, style = MaterialTheme.typography.titleLarge)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("WIND", color = secondary, style = MaterialTheme.typography.labelMedium)
                    Text(weather!!.wind ?: "—", color = primary, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (weather!!.high != null || weather!!.low != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    listOfNotNull(weather!!.high?.let { "High $it" }, weather!!.low?.let { "Low $it" })
                        .joinToString("     "),
                    color = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text(
                if (loading) "Getting weather…" else status,
                color = secondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!locationGranted && !loading) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                ) { Text("Allow location") }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun hasWeatherLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
