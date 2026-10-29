package com.insomdroid.dislauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun HomeHeader(
    preferences: LauncherPreferences, timeText: String, dateText: String,
    today: LocalDate, now: LocalTime, isDark: Boolean, fontFamily: FontFamily,
    primaryText: Color, secondaryText: Color, onOpenClock: () -> Unit, onOpenCalendar: () -> Unit
) {
    // Clock styles deliberately use fixed bundled fonts, independent of the user's global font choice.
    val dotMatrixFont = remember {
        FontFamily(
            Font(R.font.doto_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.doto_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(R.font.doto_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
        )
    }
    val interFont = remember {
        FontFamily(
            Font(R.font.inter_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.inter_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(R.font.inter_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
        )
    }
    val geistMonoFont = remember {
        FontFamily(
            Font(R.font.geist_mono_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.geist_mono_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(R.font.geist_mono_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
        )
    }
    val clockModifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable(onClick = onOpenClock)
    val dateModifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable(onClick = onOpenCalendar)
    val locale = Locale.getDefault()
    val day = today.format(DateTimeFormatter.ofPattern("EEEE", locale))
    val shortDate = today.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
    val month = today.format(DateTimeFormatter.ofPattern("MMMM", locale))
    when (preferences.clockStyle) {
        "compact" -> {
            if (preferences.showClock) Text(timeText, color=primaryText, fontSize=HomeDesign.clockSize, lineHeight=HomeDesign.clockLineHeight, letterSpacing=(-0.6).sp, fontWeight=FontWeight.Light, fontFamily=dotMatrixFont, maxLines=1, softWrap=false, modifier=clockModifier)
            if (preferences.showDate) { Spacer(Modifier.height(4.dp)); Text(shortDate, color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=dateModifier) }
        }
        "date_focus" -> {
            if (preferences.showDate) {
                Text(day.uppercase(locale), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, modifier=dateModifier)
                Text(today.dayOfMonth.toString(), color=primaryText, fontSize=HomeDesign.clockSize * 1.25f, lineHeight=HomeDesign.clockLineHeight * 1.2f, fontWeight=FontWeight.SemiBold, fontFamily = dotMatrixFont, maxLines=1, modifier=dateModifier)
                Text("$month ${today.year}", color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, modifier=dateModifier)
            }
            if (preferences.showClock) { Spacer(Modifier.height(4.dp)); Text(timeText, color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, modifier=clockModifier) }
        }
        "split" -> Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if (preferences.showClock) Column {
                Text(timeText, color=primaryText, fontSize=HomeDesign.clockSize, lineHeight=HomeDesign.clockLineHeight, letterSpacing=(-0.6).sp, fontWeight=FontWeight.Light, fontFamily = dotMatrixFont, maxLines=1, softWrap=false, modifier=clockModifier)
                Text(day.uppercase(locale), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, overflow=TextOverflow.Ellipsis)
            }
            if (preferences.showClock && preferences.showDate) Box(Modifier.width(1.dp).height(42.dp).background(secondaryText.copy(alpha=0.45f)))
            if (preferences.showDate) Column {
                Text(today.dayOfMonth.toString(), color=primaryText, fontSize=HomeDesign.clockSize, lineHeight=HomeDesign.clockLineHeight, fontWeight=FontWeight.Light, fontFamily = dotMatrixFont, maxLines=1, modifier=dateModifier)
                Text(today.format(DateTimeFormatter.ofPattern("MMM yyyy", locale)).uppercase(locale), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=dateModifier)
            }
        }
        "editorial" -> {
            if (preferences.showDate) {
                Text(day.uppercase(locale), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, modifier=dateModifier)
                Text(month.uppercase(locale), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, modifier=dateModifier)
                Text(today.dayOfMonth.toString(), color=primaryText, fontSize=HomeDesign.clockSize * 1.35f, lineHeight=HomeDesign.clockLineHeight * 1.25f, fontWeight=FontWeight.Light, fontFamily = FontFamily.Serif, maxLines=1, modifier=dateModifier)
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(today.year.toString(), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont)
                    if (preferences.showClock) Text(timeText, color=primaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, modifier=clockModifier)
                }
            } else if (preferences.showClock) Text(timeText, color=primaryText, fontSize=HomeDesign.clockSize, lineHeight=HomeDesign.clockLineHeight, fontWeight=FontWeight.Light, fontFamily = interFont, maxLines=1, modifier=clockModifier)
        }
        "digital" -> {
            if (preferences.showDate) Text(shortDate.uppercase(locale), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=dateModifier)
            if (preferences.showClock) { Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth(HomeDesign.progressTrackWidth).height(1.dp).background(secondaryText.copy(alpha=0.45f))); Spacer(Modifier.height(4.dp)); Text(timeText, color=primaryText, fontSize=HomeDesign.clockSize, lineHeight=HomeDesign.clockLineHeight, letterSpacing=(-0.6).sp, fontWeight=FontWeight.Light, fontFamily = geistMonoFont, maxLines=1, softWrap=false, modifier=clockModifier); Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth(HomeDesign.progressTrackWidth).height(1.dp).background(secondaryText.copy(alpha=0.45f))) }
        }
        "vertical_stack" -> {
            val hourPart = timeText.substringBefore(":").trim()
            val minutePart = timeText.substringAfter(":", "").take(2)
            if (preferences.showClock) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = clockModifier) {
                    Text(hourPart, color = primaryText, fontSize = HomeDesign.clockSize * 0.9f, lineHeight = HomeDesign.clockLineHeight * 0.88f, letterSpacing = (-0.6).sp, fontWeight = FontWeight.Light, fontFamily = dotMatrixFont, maxLines = 1, softWrap = false)
                    Text(minutePart, color = primaryText, fontSize = HomeDesign.clockSize * 0.9f, lineHeight = HomeDesign.clockLineHeight * 0.88f, letterSpacing = (-0.6).sp, fontWeight = FontWeight.Light, fontFamily = dotMatrixFont, maxLines = 1, softWrap = false)
                }
            }
            if (preferences.showDate) { Spacer(Modifier.height(4.dp)); Text(shortDate, color = secondaryText, fontSize = HomeDesign.dateSize, lineHeight = HomeDesign.dateLineHeight, fontFamily = interFont, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = dateModifier) }
        }
        "binary_dot_minimal" -> {
            if (preferences.showClock) {
                Text(timeText, color = secondaryText, fontSize = HomeDesign.dateSize, lineHeight = HomeDesign.dateLineHeight, fontFamily = interFont, maxLines = 1, softWrap = false, modifier = clockModifier)
                Spacer(Modifier.height(6.dp))
                BinaryDotClock(hour = now.hour, minute = now.minute, activeColor = MaterialTheme.colorScheme.primary, inactiveColor = secondaryText.copy(alpha = 0.24f))
            }
        }
        "dual_bar" -> {
            if (preferences.showClock) {
                Text(timeText, color = secondaryText, fontSize = HomeDesign.dateSize, lineHeight = HomeDesign.dateLineHeight, fontFamily = interFont, maxLines = 1, softWrap = false, modifier = clockModifier)
                Spacer(Modifier.height(6.dp))
                DualBarClock(hour = now.hour, minute = now.minute, activeColor = MaterialTheme.colorScheme.primary, inactiveColor = secondaryText.copy(alpha = 0.24f))
            }
            if (preferences.showDate) { Spacer(Modifier.height(4.dp)); Text(shortDate, color = secondaryText, fontSize = HomeDesign.dateSize, lineHeight = HomeDesign.dateLineHeight, fontFamily = interFont, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = dateModifier) }
        }
        "text_clock" -> {
            val hourWord = numberWord(if (now.hour % 12 == 0) 12 else now.hour % 12)
            val minuteWord = numberWord(now.minute)
            if (preferences.showClock) {
                Text(hourWord, color=primaryText, fontSize=HomeDesign.clockSize * 0.78f, lineHeight=HomeDesign.clockLineHeight * 0.82f, fontWeight=FontWeight.Medium, fontFamily=dotMatrixFont, maxLines=1, softWrap=false, modifier=clockModifier)
            }
            if (preferences.showDate) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(28.dp).height(1.dp).background(secondaryText.copy(alpha=0.65f)))
                    Text(today.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily=interFont, maxLines=1, modifier=dateModifier)
                }
            }
            if (preferences.showClock) {
                Spacer(Modifier.height(4.dp))
                Text(minuteWord, color=primaryText, fontSize=HomeDesign.clockSize * 0.78f, lineHeight=HomeDesign.clockLineHeight * 0.82f, fontWeight=FontWeight.Medium, fontFamily=dotMatrixFont, maxLines=1, softWrap=false, modifier=clockModifier)
            }
        }
        else -> {
            if (preferences.showDate) Text(day.uppercase(locale), color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=dateModifier)
            if (preferences.showClock) Text(timeText, color=primaryText, fontSize=HomeDesign.clockSize, lineHeight=HomeDesign.clockLineHeight, letterSpacing=(-0.6).sp, fontWeight=FontWeight.Light, fontFamily = fontFamily, maxLines=1, softWrap=false, modifier=clockModifier)
            if (preferences.showDate) { Spacer(Modifier.height(HomeDesign.clockToDate)); Text(dateText, color=secondaryText, fontSize=HomeDesign.dateSize, lineHeight=HomeDesign.dateLineHeight, fontFamily = interFont, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=dateModifier) }
        }
    }
    if (preferences.showYearProgress || preferences.showDayProgress) {
        Spacer(Modifier.height(HomeDesign.progressLineGap))
        if (preferences.showYearProgress) ProgressLine(label="Year in progress", percent=(today.dayOfYear*100f/today.lengthOfYear()).toInt(), primaryText=primaryText, secondaryText=secondaryText, fill=MaterialTheme.colorScheme.primary, track=if(isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest)
        if (preferences.showDayProgress) {
            if (preferences.showYearProgress) Spacer(Modifier.height(HomeDesign.progressLineGap))
            ProgressLine(label="Day in progress", percent=(now.toSecondOfDay()*100f/86400f).toInt(), primaryText=primaryText, secondaryText=secondaryText, fill=MaterialTheme.colorScheme.primary, track=if(isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}

private fun numberWord(value: Int): String {
    val units = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty")
    return when {
        value < 0 -> ""
        value < 20 -> units[value]
        value < 60 -> tens[value / 10] + if (value % 10 == 0) "" else " " + units[value % 10]
        else -> value.toString()
    }
}

@Composable
private fun BinaryDotClock(hour: Int, minute: Int, activeColor: Color, inactiveColor: Color) {
    Canvas(modifier = Modifier.width(88.dp).height(24.dp)) {
        val dotRadius = 2.4.dp.toPx()
        val gap = 4.5.dp.toPx()
        val rowGap = 8.dp.toPx()
        val hourBits = hour.coerceIn(0, 23).toString(2).padStart(5, '0')
        val minuteBits = minute.coerceIn(0, 59).toString(2).padStart(6, '0')
        val rows = listOf(hourBits, minuteBits)
        rows.forEachIndexed { rowIndex, bits ->
            val y = dotRadius + rowIndex * (dotRadius * 2 + rowGap)
            val startX = if (rowIndex == 0) 0f else 0f
            bits.forEachIndexed { index, bit ->
                val x = startX + dotRadius + index * (dotRadius * 2 + gap)
                drawCircle(if (bit == '1') activeColor else inactiveColor, dotRadius, Offset(x, y))
            }
        }
    }
}

@Composable
private fun DualBarClock(hour: Int, minute: Int, activeColor: Color, inactiveColor: Color) {
    Canvas(modifier = Modifier.width(94.dp).height(34.dp)) {
        val barWidth = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val groupGap = 10.dp.toPx()
        val maxHeight = size.height
        val baseline = size.height
        val hourBits = hour.coerceIn(0, 23).toString(2).padStart(5, '0')
        val minuteBits = minute.coerceIn(0, 59).toString(2).padStart(6, '0')
        var x = 0f
        listOf(hourBits, minuteBits).forEachIndexed { groupIndex, bits ->
            bits.forEach { bit ->
                val barHeight = if (bit == '1') maxHeight else maxHeight * 0.22f
                drawRoundRect(
                    color = if (bit == '1') activeColor else inactiveColor,
                    topLeft = Offset(x, baseline - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
                x += barWidth + gap
            }
            if (groupIndex == 0) x += groupGap - gap
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
        verticalArrangement = Arrangement.spacedBy(HomeDesign.progressLabelGap)
    ) {
        Text(
            "$label $percent%",
            color = primaryText,
            fontSize = HomeDesign.progressLabelSize,
            lineHeight = HomeDesign.progressLabelLineHeight,
            fontWeight = FontWeight.Normal
        )
        Canvas(
            modifier = Modifier.fillMaxWidth(HomeDesign.progressTrackWidth).height(HomeDesign.progressTrackHeight)
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
