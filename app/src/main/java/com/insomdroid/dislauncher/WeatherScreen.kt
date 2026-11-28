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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val currentLocale = configuration.locales[0]
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
        } else if (shouldBackoffWeatherFetch(context)) {
            // Respect the same persisted retry schedule as the home-screen weather widget.
            // This prevents repeated network requests whenever the weather screen is reopened.
            weather = cachedWeather?.let { convertWeatherUnit(it, weatherUnit) }
            loading = false
            status = if (weather == null) {
                "Weather is temporarily unavailable. Try again later."
            } else {
                "Showing saved weather while waiting to retry."
            }
        } else {
            loading = true
            status = "Getting weather…"
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, weatherUnit) }
            recordWeatherFetchResult(context, succeeded = fetched != null)
            weather = fetched ?: cachedWeather?.let { convertWeatherUnit(it, weatherUnit) }
            status = if (fetched == null) "Weather is unavailable. Showing saved weather if available." else ""
            loading = false
            onWeatherChange(fetched ?: cachedWeather)
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
                modifier = Modifier.fillMaxWidth().weight(1f).offset(y = if (isLandscape) 0.dp else (-72).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    currentWeather.locationName ?: "Weather",
                    color = secondary,
                    fontSize = if (isLandscape) 16.sp else 22.sp,
                    lineHeight = if (isLandscape) 20.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(if (isLandscape) 4.dp else 14.dp))
                WeatherIcon(currentWeather.condition, primary, Modifier.size(if (isLandscape) 88.dp else 164.dp))
                Spacer(Modifier.height(if (isLandscape) 4.dp else 10.dp))
                Text(
                    currentWeather.temperature,
                    color = primary,
                    fontSize = if (isLandscape) 38.sp else 52.sp,
                    lineHeight = if (isLandscape) 42.sp else 60.sp,
                    letterSpacing = (-1.2).sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentWeather.condition.lowercase(currentLocale).replaceFirstChar { it.uppercase() },
                    color = secondary,
                    fontSize = if (isLandscape) 14.sp else 18.sp,
                    lineHeight = if (isLandscape) 18.sp else 24.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (currentWeather.feelsLike != null || currentWeather.high != null || currentWeather.low != null) {
                    Spacer(Modifier.height(if (isLandscape) 8.dp else 26.dp))
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
                modifier = Modifier.fillMaxWidth().weight(1f).offset(y = if (isLandscape) 0.dp else (-72).dp),
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


