package com.insomdroid.dislauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun HomeHeader(
    preferences: LauncherPreferences,
    timeText: String,
    dateText: String,
    today: LocalDate,
    now: LocalTime,
    isDark: Boolean,
    fontFamily: FontFamily,
    primaryText: Color,
    secondaryText: Color,
    onOpenClock: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    if (preferences.showClock) {
        Text(
            timeText,
            color = primaryText,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.6).sp,
            fontWeight = FontWeight.Light,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clip(RoundedCornerShape(5.dp))
                .clickable(onClick = onOpenClock)
        )
    }
    if (preferences.showDate) {
        Spacer(Modifier.height(4.dp))
        Text(
            dateText,
            color = secondaryText,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable(onClick = onOpenCalendar)
        )
    }
    if (preferences.showYearProgress || preferences.showDayProgress) {
        Spacer(Modifier.height(5.dp))
        if (preferences.showYearProgress) {
            ProgressLine(
                label = "Year in progress",
                percent = (today.dayOfYear * 100f / today.lengthOfYear()).toInt(),
                primaryText = primaryText,
                secondaryText = secondaryText,
                fill = primaryText,
                track = if (isDark) Color(0xFF555555) else Color(0xFFD5D5D5),
            )
        }
        if (preferences.showDayProgress) {
            if (preferences.showYearProgress) Spacer(Modifier.height(2.dp))
            ProgressLine(
                label = "Day in progress",
                percent = (now.toSecondOfDay() * 100f / 86400f).toInt(),
                primaryText = primaryText,
                secondaryText = secondaryText,
                fill = primaryText,
                track = if (isDark) Color(0xFF555555) else Color(0xFFD5D5D5),
            )
        }
    }
}

@Composable
internal fun ProgressLine(
    label: String,
    percent: Int,
    primaryText: Color,
    secondaryText: Color,
    fill: Color,
    track: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "$label $percent%",
            color = primaryText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal
        )
        Canvas(
            modifier = Modifier.fillMaxWidth(0.36f).height(13.dp)
        ) {
            val stepX = 4.dp.toPx()
            val stepY = 4.dp.toPx()
            val radius = 1.1.dp.toPx()
            val columns = (size.width / stepX).toInt().coerceAtLeast(1)
            val rows = (size.height / stepY).toInt().coerceAtLeast(1)
            for (row in 0..rows) {
                for (column in 0..columns) {
                    val x = column * stepX + radius
                    val y = row * stepY + radius
                    if (x <= size.width && y <= size.height) {
                        val color = if (x / size.width * 100f <= percent.coerceIn(0, 100)) {
                            fill
                        } else {
                            track.copy(alpha = 0.85f)
                        }
                        drawCircle(color = color, radius = radius, center = Offset(x, y))
                    }
                }
            }
        }
    }
}
