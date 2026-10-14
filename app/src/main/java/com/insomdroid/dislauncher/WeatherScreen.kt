package com.insomdroid.dislauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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
            status = "Allow location access to get local weather."
        } else {
            // Fetch only when Home has no cached result. There is no manual refresh action.
            loading = true
            status = "Getting weather…"
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, weatherUnit) }
            weather = fetched
            status = if (fetched == null) "Weather is unavailable. Check location and internet." else ""
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(50.dp)
        ) {
            Text(
                "←",
                color = primary,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                modifier = Modifier.align(Alignment.CenterStart)
                    .clickable(onClick = onBack)
                    .padding(end = 12.dp)
            )
            Text(
                weather?.locationName ?: cachedWeather?.locationName ?: "Weather",
                color = primary,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 42.dp)
            )
        }

        if (weather != null) {
            Spacer(Modifier.weight(0.8f))
            WeatherIcon(
                weather!!.condition,
                primary,
                Modifier.size(260.dp)
            )
            Spacer(Modifier.weight(1.2f))
            Text(
                "${weather!!.temperature} ${weather!!.condition.lowercase(Locale.getDefault())}",
                color = primary,
                fontSize = 35.sp,
                lineHeight = 43.sp,
                letterSpacing = (-0.9).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            weather!!.feelsLike?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    "feels like $it",
                    color = primary,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (weather!!.high != null || weather!!.low != null) {
                Spacer(Modifier.height(17.dp))
                Text(
                    listOfNotNull(weather!!.high?.let { "H: $it" }, weather!!.low?.let { "L: $it" })
                        .joinToString("  "),
                    color = primary,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            Text(
                if (loading) "Getting weather…" else status,
                color = secondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
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
