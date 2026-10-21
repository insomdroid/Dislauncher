package com.insomdroid.dislauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
            fontSize = 34.sp,
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
        Spacer(Modifier.height(2.dp))
        Text(
            dateText,
            color = secondaryText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontFamily = fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable(onClick = onOpenCalendar)
        )
    }
    if (preferences.showYearProgress || preferences.showDayProgress) {
        Spacer(Modifier.height(4.dp))
        if (preferences.showYearProgress) {
            ProgressLine(
                label = "Year in progress",
                percent = (today.dayOfYear * 100f / today.lengthOfYear()).toInt(),
                primaryText = primaryText,
                secondaryText = secondaryText,
                fill = MaterialTheme.colorScheme.primary,
                track = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
        if (preferences.showDayProgress) {
            if (preferences.showYearProgress) Spacer(Modifier.height(4.dp))
            ProgressLine(
                label = "Day in progress",
                percent = (now.toSecondOfDay() * 100f / 86400f).toInt(),
                primaryText = primaryText,
                secondaryText = secondaryText,
                fill = MaterialTheme.colorScheme.primary,
                track = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest,
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
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = primaryText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                "$percent%",
                color = secondaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Canvas(
            modifier = Modifier.fillMaxWidth(0.40f).height(4.dp)
        ) {
            val centerY = size.height / 2f
            val strokeWidth = size.height
            val progressFraction = percent.coerceIn(0, 100) / 100f
            drawLine(
                color = track.copy(alpha = 0.85f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Butt
            )
            if (progressFraction > 0f) {
                drawLine(
                    color = fill,
                    start = Offset(0f, centerY),
                    end = Offset(size.width * progressFraction, centerY),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Butt
                )
            }
        }
    }
}
