package com.insomdroid.dislauncher

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class BuiltInTool { NOTES, CALCULATOR, CALENDAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuiltInToolScreen(tool: BuiltInTool, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(onBack = onBack)
    val title = when (tool) {
        BuiltInTool.NOTES -> "Notes"
        BuiltInTool.CALCULATOR -> "Calculator"
        BuiltInTool.CALENDAR -> "Calendar"
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tool) {
                BuiltInTool.NOTES -> NotesTool(context, Modifier.weight(1f))
                BuiltInTool.CALCULATOR -> CalculatorTool()
                BuiltInTool.CALENDAR -> CalendarTool()
            }
        }
    }
}

@Composable
private fun NotesTool(context: Context, modifier: Modifier = Modifier) {
    val store = remember(context) { context.getSharedPreferences("dislauncher_notes", Context.MODE_PRIVATE) }
    var note by remember { mutableStateOf(store.getString("content", "") ?: "") }
    Text("Your notes stay on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = note,
        onValueChange = {
            note = it
            store.edit().putString("content", it).apply()
        },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Write something…") },
        minLines = 8,
        maxLines = Int.MAX_VALUE
    )
}

@Composable
private fun CalculatorTool() {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    OutlinedTextField(
        value = expression,
        onValueChange = { expression = it; result = "" },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Expression") },
        placeholder = { Text("12 × (3 + 4)") }
    )
    val keys = listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", "0", ".", "(", ")", "+", "⌫", "C", "=")
    keys.chunked(4).forEach { rowKeys ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            rowKeys.forEach { key ->
                TextButton(
                    onClick = {
                        when (key) {
                            "C" -> { expression = ""; result = "" }
                            "⌫" -> { expression = expression.dropLast(1); result = "" }
                            "=" -> result = runCatching { formatCalculation(ExpressionParser(expression).parse()) }
                                .getOrElse { "Invalid expression" }
                            else -> {
                                expression += when (key) {
                                    "×" -> "*"
                                    "÷" -> "/"
                                    "−" -> "-"
                                    else -> key
                                }
                                result = ""
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(key, fontSize = 18.sp) }
            }
            repeat(4 - rowKeys.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
}

private fun formatCalculation(value: Double): String =
    if (!value.isFinite()) "Cannot calculate"
    else if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.')

private class ExpressionParser(raw: String) {
    private val input = raw.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")
    private var index = 0
    fun parse(): Double {
        require(input.isNotBlank())
        val value = expression()
        require(index == input.length)
        require(value.isFinite())
        return value
    }
    private fun expression(): Double {
        var value = term()
        while (index < input.length && (input[index] == '+' || input[index] == '-')) {
            val op = input[index++]
            val rhs = term()
            value = if (op == '+') value + rhs else value - rhs
        }
        return value
    }
    private fun term(): Double {
        var value = primary()
        while (index < input.length && (input[index] == '*' || input[index] == '/')) {
            val op = input[index++]
            val rhs = primary()
            value = if (op == '*') value * rhs else {
                require(rhs != 0.0)
                value / rhs
            }
        }
        return value
    }
    private fun primary(): Double {
        if (index < input.length && (input[index] == '+' || input[index] == '-')) {
            val op = input[index++]
            val value = primary()
            return if (op == '-') -value else value
        }
        if (index < input.length && input[index] == '(') {
            index++
            val value = expression()
            require(index < input.length && input[index] == ')')
            index++
            return value
        }
        val start = index
        var dots = 0
        while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
            if (input[index] == '.') dots++
            require(dots <= 1)
            index++
        }
        require(index > start)
        return input.substring(start, index).toDouble()
    }
}

@Composable
private fun CalendarTool() {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val cells = List(firstOffset) { 0 } + (1..month.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f).height(42.dp).padding(2.dp)
                            .then(if (day > 0) Modifier.clickable { selectedDate = month.atDay(day) } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day > 0) {
                            val date = month.atDay(day)
                            val selected = date == selectedDate
                            Text(
                                day.toString(),
                                modifier = if (selected) Modifier.background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape).padding(horizontal = 10.dp, vertical = 5.dp) else Modifier,
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    date == today -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onBackground
                                }
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleMedium)
        Text(if (selectedDate == today) "Today" else "Selected date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
