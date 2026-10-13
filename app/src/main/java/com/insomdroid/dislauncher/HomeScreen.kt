package com.insomdroid.dislauncher

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val inspirationQuotes = listOf(
    "Focus on what matters.",
    "Do less, with more intention.",
    "Make room for what matters.",
    "Attention is your most precious resource.",
    "Small steps still move you forward.",
    "Be where your feet are.",
    "Choose presence over noise.",
    "Progress, not pressure."
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    apps: List<LauncherApp>,
    favorites: Set<String>,
    showProgress: Boolean,
    showQuote: Boolean,
    isDark: Boolean,
    fontFamily: FontFamily,
    onLaunch: (LauncherApp) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    var quoteIndex by remember { mutableIntStateOf(LocalDate.now().dayOfYear % inspirationQuotes.size) }
    val today = LocalDate.now()
    val timeFormat = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    val timeText = now.format(DateTimeFormatter.ofPattern(timeFormat, Locale.getDefault()))
    val background = if (isDark) Color.Black else Color(0xFFFFFCF7)
    val primaryText = if (isDark) Color(0xFFF6F3FA) else Color(0xFF1A191D)
    val secondaryText = if (isDark) Color(0xFFA8A5AE) else Color(0xFF68656D)
    val visibleFavorites = remember(apps, favorites) {
        apps.filter { it.packageName in favorites }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    val batteryPercent = remember { readBatteryPercent(context) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalTime.now()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            timeText,
            color = primaryText,
            fontSize = 46.sp,
            lineHeight = 50.sp,
            letterSpacing = (-2.4).sp,
            fontWeight = FontWeight.Light,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(2.dp))
        Text(dateText, color = secondaryText, fontSize = 12.sp, fontFamily = fontFamily, maxLines = 1,
            overflow = TextOverflow.Ellipsis)

        if (showProgress) {
            Spacer(Modifier.height(28.dp))
            ProgressLine(
                label = "YEAR IN PROGRESS",
                percent = (today.dayOfYear * 100f / today.lengthOfYear()).toInt(),
                primaryText = primaryText, secondaryText = secondaryText,
                fill = if (isDark) Color.White else Color(0xFF25232A),
                track = if (isDark) Color(0xFF29282D) else Color(0xFFE8E3DD)
            )
            Spacer(Modifier.height(15.dp))
            ProgressLine(
                label = "DAY IN PROGRESS",
                percent = (now.toSecondOfDay() * 100f / 86400f).toInt(),
                primaryText = primaryText, secondaryText = secondaryText,
                fill = if (isDark) Color.White else Color(0xFF25232A),
                track = if (isDark) Color(0xFF29282D) else Color(0xFFE8E3DD)
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AT A GLANCE", color = secondaryText, fontSize = 9.sp, letterSpacing = 1.2.sp)
            Text(batteryPercent?.let { "BATTERY  $it%" } ?: "BATTERY",
                color = secondaryText, fontSize = 9.sp, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp)
            .background(if (isDark) Color(0xFF343238) else Color(0xFFE4DFDA)))
        Spacer(Modifier.height(18.dp))

        if (showQuote) {
            Text(
                text = "“${inspirationQuotes[quoteIndex]}”",
                color = secondaryText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontFamily = fontFamily,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { quoteIndex = (quoteIndex + 1) % inspirationQuotes.size }
                    .padding(vertical = 4.dp)
            )
            Spacer(Modifier.height(20.dp))
        }

        Text("YOUR APPS", color = secondaryText, fontSize = 9.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        if (visibleFavorites.isEmpty()) {
            Text("Your space, your pace.", color = primaryText, fontSize = 18.sp, fontFamily = fontFamily)
            Spacer(Modifier.height(4.dp))
            Text("Open All apps and hold an app name to add it here.",
                color = secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
        } else {
            visibleFavorites.forEach { app ->
                Text(
                    text = app.label,
                    color = primaryText,
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { onLaunch(app) },
                        onLongClick = { onToggleFavorite(app.packageName) }
                    ).padding(vertical = 5.dp)
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("All apps  →", color = primaryText, fontSize = 14.sp, fontFamily = fontFamily,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenApps)
                .padding(vertical = 8.dp, horizontal = 1.dp))
        Spacer(Modifier.height(5.dp))
        Text("Settings", color = secondaryText, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenSettings)
                .padding(vertical = 7.dp, horizontal = 1.dp))
        Spacer(Modifier.height(20.dp))
        Text("SWIPE LEFT FOR APPS", color = secondaryText, fontSize = 8.sp, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun ProgressLine(
    label: String,
    percent: Int,
    primaryText: Color,
    secondaryText: Color,
    fill: Color,
    track: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = secondaryText, fontSize = 9.sp, letterSpacing = 0.55.sp)
            Text("$percent%", color = primaryText, fontSize = 10.sp)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(track)) {
            Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(3.dp)
                .clip(RoundedCornerShape(99.dp)).background(fill))
        }
    }
}
