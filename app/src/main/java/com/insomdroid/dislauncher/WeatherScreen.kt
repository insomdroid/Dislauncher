package com.insomdroid.dislauncher

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun WeatherScreen(
    weatherUnit: String,
    cachedWeather: WeatherInfo?,
    onWeatherChange: (WeatherInfo?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var locationGranted by remember { mutableStateOf(hasWeatherLocationPermission(context)) }
    var weather by remember {
        mutableStateOf(cachedWeather?.let { convertWeatherUnit(it, weatherUnit) })
    }
    var status by remember { mutableStateOf("Location permission is required.") }
    var loading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationGranted = granted || hasWeatherLocationPermission(context)
        if (!locationGranted) status = "Location permission was denied."
    }

    LaunchedEffect(weatherUnit, locationGranted, cachedWeather) {
        val matchingCache = cachedWeather?.let { convertWeatherUnit(it, weatherUnit) }
            ?.takeIf { isWeatherCacheFresh(context) }
        if (matchingCache != null) {
            weather = matchingCache
            loading = false
            status = ""
        } else if (!locationGranted) {
            weather = cachedWeather
            loading = false
            status = if (cachedWeather == null) "Allow location access to get local weather." else ""
        } else {
            loading = true
            status = "Getting weather…"
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, weatherUnit) }
            recordWeatherFetchResult(context, succeeded = fetched != null)
            weather = fetched ?: cachedWeather?.let { convertWeatherUnit(it, weatherUnit) }
            status = if (fetched == null) "Weather is unavailable. Showing saved weather if available." else ""
            loading = false
            onWeatherChange(fetched)
        }
    }

    val currentWeather = weather
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(
                "←",
                color = primary,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                modifier = Modifier.align(Alignment.CenterStart)
                    .clickable(onClick = onBack)
                    .padding(end = 12.dp)
            )
        }

        if (currentWeather != null) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).offset(y = (-72).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    currentWeather.locationName ?: "Weather",
                    color = secondary,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(14.dp))
                WeatherIcon(currentWeather.condition, primary, Modifier.size(164.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    currentWeather.temperature,
                    color = primary,
                    fontSize = 52.sp,
                    lineHeight = 60.sp,
                    letterSpacing = (-1.2).sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentWeather.condition.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() },
                    color = secondary,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (currentWeather.feelsLike != null || currentWeather.high != null || currentWeather.low != null) {
                    Spacer(Modifier.height(26.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        currentWeather.feelsLike?.let {
                            WeatherMetric("FEELS LIKE", it, primary, secondary)
                        }
                        if (currentWeather.high != null || currentWeather.low != null) {
                            val highLow = "H: " + (currentWeather.high ?: "—") + "   L: " + (currentWeather.low ?: "—")
                            WeatherMetric("HIGH / LOW", highLow, primary, secondary)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).offset(y = (-72).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (loading) "Getting weather…" else status,
                    color = secondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                if (!locationGranted && !loading) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    ) { Text("Allow location") }
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(
    label: String,
    value: String,
    primary: Color,
    secondary: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = secondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(value, color = primary, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


