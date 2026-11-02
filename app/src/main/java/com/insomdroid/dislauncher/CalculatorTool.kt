package com.insomdroid.dislauncher

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class CalculatorHistoryEntry(
    val id: String,
    val expression: String,
    val result: String,
    val createdAt: Long
)


internal fun calculatorHistoryStore(context: Context) =
    context.getSharedPreferences("dislauncher_calculator_history", Context.MODE_PRIVATE)


internal fun loadCalculatorHistory(context: Context): List<CalculatorHistoryEntry> = runCatching {
    val array = JSONArray(calculatorHistoryStore(context).getString("history_json", "[]") ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        CalculatorHistoryEntry(
            id = item.optString("id"),
            expression = item.optString("expression"),
            result = item.optString("result"),
            createdAt = item.optLong("createdAt")
        ).takeIf { it.id.isNotBlank() && it.expression.isNotBlank() && it.result.isNotBlank() }
    }
}.getOrDefault(emptyList()).sortedByDescending { it.createdAt }.take(100)


internal fun saveCalculatorHistory(context: Context, history: List<CalculatorHistoryEntry>) {
    val array = JSONArray()
    history.take(100).forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("expression", entry.expression)
                .put("result", entry.result)
                .put("createdAt", entry.createdAt)
        )
    }
    calculatorHistoryStore(context).edit().putString("history_json", array.toString()).apply()
}


@Composable
internal fun CalculatorTool(
    modifier: Modifier = Modifier,
    showHistory: Boolean,
    onShowHistoryChange: (Boolean) -> Unit,
    clearHistorySignal: Int,
    onHistoryAvailabilityChanged: (Boolean) -> Unit,
    scientificMode: Boolean,
    onScientificModeChange: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var history by remember(context) { mutableStateOf(loadCalculatorHistory(context)) }

    LaunchedEffect(history) {
        onHistoryAvailabilityChanged(history.isNotEmpty())
    }
    LaunchedEffect(clearHistorySignal) {
        if (clearHistorySignal > 0) showClearHistoryDialog = true
    }
    val keyRows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    BoxWithConstraints(modifier = modifier) {
        val keySize = minOf(72.dp, (maxWidth - BuiltInToolSpacing.space24) / 4, (maxHeight - if (scientificMode) 240.dp else 170.dp) / 5)
            .coerceAtLeast(36.dp)

        if (showHistory) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)) {
                if (history.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BuiltInEmptyState(title = "No calculations yet")
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)) {
                        items(history, key = { it.id }) { entry ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(BuiltInToolDesign.cardCornerRadius)).clickable {
                                    expression = entry.expression
                                    result = entry.result
                                    onShowHistoryChange(false)
                                },
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = BuiltInToolSpacing.cardHorizontal, vertical = BuiltInToolSpacing.card),
                                    verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)
                                ) {
                                    Text(entry.expression, style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    Text(entry.result, style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        java.time.Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)) {
                if (scientificMode) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "sin" to "sin(", "cos" to "cos(", "tan" to "tan(",
                            "√" to "sqrt(", "ln" to "ln(", "log" to "log(",
                            "xʸ" to "^", "π" to "π", "e" to "e"
                        ).forEach { (label, token) ->
                            TextButton(onClick = { expression += token; result = "" }) {
                                Text(label, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = BuiltInToolSpacing.space2, vertical = BuiltInToolSpacing.space4),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    if (expression.isNotBlank()) {
                        Text(expression, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2)
                        Spacer(Modifier.height(BuiltInToolSpacing.space8))
                    }
                    Text(
                        result.ifBlank { expression.ifBlank { "0" } },
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 2
                    )
                }
                keyRows.forEach { rowKeys ->
                    Row(
                        Modifier.fillMaxWidth().height(keySize),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowKeys.forEach { key ->
                                val operator = key in listOf("÷", "×", "−", "+", "=")
                                val action: () -> Unit = {
                                    when (key) {
                                        "C" -> { expression = ""; result = "" }
                                        "⌫" -> { expression = expression.dropLast(1); result = "" }
                                        "=" -> {
                                            val calculated = runCatching {
                                                formatCalculation(ExpressionParser(expression).parse())
                                            }.getOrNull()
                                            if (calculated == null) {
                                                result = "Invalid expression"
                                            } else {
                                                result = calculated
                                                val entry = CalculatorHistoryEntry(
                                                    UUID.randomUUID().toString(), expression, calculated,
                                                    System.currentTimeMillis()
                                                )
                                                history = (listOf(entry) + history).take(100)
                                                saveCalculatorHistory(context, history)
                                            }
                                        }
                                        "%" -> {
                                            if (expression.lastOrNull()?.let { it.isDigit() || it == ')' } == true) {
                                                expression = applyCalculatorPercentage(expression)
                                            }
                                            result = ""
                                        }
                                        "±" -> {
                                            if (expression.isNotBlank()) expression = "-(" + expression + ")"
                                            result = ""
                                        }
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
                                }
                                val labelSize = (keySize.value / 2.35f).coerceIn(18f, 28f).sp
                                if (operator) {
                                    Button(onClick = action, modifier = Modifier.size(keySize), shape = RoundedCornerShape(BuiltInToolDesign.calculatorKeyCornerRadius),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                } else {
                                    FilledTonalButton(onClick = action, modifier = Modifier.size(keySize), shape = RoundedCornerShape(BuiltInToolDesign.calculatorKeyCornerRadius),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                }
                        }
                    }
                }
            }
        }    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear calculator history?") },
            text = { Text("Saved calculations will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    history = emptyList()
                    saveCalculatorHistory(context, history)
                    showClearHistoryDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } }
        )
    }
}
