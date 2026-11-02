package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.time.YearMonth

internal enum class BuiltInTool { NOTES, CALCULATOR, CALENDAR, UNIT_CONVERTER, BOOKS }


internal const val EXTRA_BUILT_IN_TOOL = "com.insomdroid.dislauncher.BUILT_IN_TOOL"


internal fun launchBuiltInTool(context: Context, tool: BuiltInTool) {
    context.startActivity(
        Intent(context, BuiltInToolActivity::class.java)
            .putExtra(EXTRA_BUILT_IN_TOOL, tool.name)
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuiltInToolScreen(tool: BuiltInTool, onBack: () -> Unit) {
    if (tool == BuiltInTool.BOOKS) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            BooksTool(onBack = onBack)
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var notesEditing by remember { mutableStateOf(false) }
    var notesBackSignal by remember { mutableStateOf(0) }
    var calculatorShowingHistory by remember { mutableStateOf(false) }
    var calculatorScientificMode by remember { mutableStateOf(false) }
    var calculatorHistoryHasItems by remember { mutableStateOf(false) }
    var calculatorClearHistorySignal by remember { mutableStateOf(0) }
    var calendarMonth by remember { mutableStateOf(YearMonth.now()) }
    var converterSearchExpanded by remember { mutableStateOf(false) }
    var converterSearchQuery by remember { mutableStateOf("") }
    fun requestClose() = onBack()
    BackHandler(enabled = tool != BuiltInTool.NOTES || !notesEditing) {
        if (tool == BuiltInTool.CALCULATOR && calculatorShowingHistory) {
            calculatorShowingHistory = false
        } else {
            requestClose()
        }
    }
    val title = when (tool) {
        BuiltInTool.NOTES -> "Notes"
        BuiltInTool.CALCULATOR -> if (calculatorShowingHistory) "History" else "Calculator"
        BuiltInTool.CALENDAR -> "Calendar"
        BuiltInTool.UNIT_CONVERTER -> "Unit Converter"
    }
    if (tool == BuiltInTool.NOTES) {
        NotesTool(
            context = context,
            backSignal = notesBackSignal,
            onEditingChanged = { notesEditing = it },
            onBackRequested = { if (notesEditing) notesBackSignal++ else requestClose() },
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    BuiltInToolScaffold(
                header = {
                    BuiltInToolHeader(
                        title = {
                            if (tool == BuiltInTool.UNIT_CONVERTER && converterSearchExpanded) {
                                BuiltInToolSearchField(
                                    value = converterSearchQuery,
                                    onValueChange = { converterSearchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "Search units and categories"
                                )
                            } else {
                                Text(
                                    title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                when {
                                    tool == BuiltInTool.NOTES && notesEditing -> notesBackSignal++
                                    tool == BuiltInTool.CALCULATOR && calculatorShowingHistory -> calculatorShowingHistory = false
                                    else -> requestClose()
                                }
                            }) {
                                Icon(painterResource(R.drawable.ic_symbol_arrow_back), contentDescription = "Back", modifier = Modifier.size(LauncherIconTokens.standard))
                            }
                        },
                        actions = {
                            if (tool == BuiltInTool.UNIT_CONVERTER) {
                                if (converterSearchExpanded) {
                                    IconButton(onClick = {
                                        converterSearchQuery = ""
                                        converterSearchExpanded = false
                                    }) {
                                        Icon(painterResource(R.drawable.ic_symbol_close), contentDescription = "Close search", modifier = Modifier.size(LauncherIconTokens.standard))
                                    }
                                } else {
                                    IconButton(onClick = { converterSearchExpanded = true }) {
                                        Icon(painterResource(R.drawable.ic_symbol_search), contentDescription = "Search converter", modifier = Modifier.size(LauncherIconTokens.standard))
                                    }
                                }
                            }
                            if (tool == BuiltInTool.CALENDAR) {
                                IconButton(onClick = { calendarMonth = calendarMonth.minusMonths(1) }) {
                                    Icon(painterResource(R.drawable.ic_symbol_arrow_back), contentDescription = "Previous month", modifier = Modifier.size(LauncherIconTokens.standard))
                                }
                                IconButton(onClick = { calendarMonth = calendarMonth.plusMonths(1) }) {
                                    Icon(painterResource(R.drawable.ic_symbol_arrow_forward), contentDescription = "Next month", modifier = Modifier.size(LauncherIconTokens.standard))
                                }
                            }
                            if (tool == BuiltInTool.CALCULATOR && !calculatorShowingHistory) {
                                TextButton(onClick = { calculatorScientificMode = !calculatorScientificMode }) {
                                    Text(if (calculatorScientificMode) "Basic" else "Scientific")
                                }
                            }
                            if (tool == BuiltInTool.CALCULATOR) {
                                if (calculatorShowingHistory) {
                                    IconButton(
                                        onClick = { calculatorClearHistorySignal++ },
                                        enabled = calculatorHistoryHasItems
                                    ) {
                                        Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = "Clear calculator history", modifier = Modifier.size(LauncherIconTokens.standard))
                                    }
                                } else {
                                    IconButton(onClick = { calculatorShowingHistory = true }) {
                                        Icon(painterResource(R.drawable.ic_symbol_history), contentDescription = "Open calculator history", modifier = Modifier.size(LauncherIconTokens.standard))
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) {
                Box(Modifier.fillMaxSize()) {
                        when (tool) {
                            BuiltInTool.CALCULATOR -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                CalculatorTool(
                                    modifier = Modifier.widthIn(max = BuiltInToolDesign.calculatorMaxWidth).fillMaxSize(),
                                    showHistory = calculatorShowingHistory,
                                    onShowHistoryChange = { calculatorShowingHistory = it },
                                    clearHistorySignal = calculatorClearHistorySignal,
                                    onHistoryAvailabilityChanged = { calculatorHistoryHasItems = it },
                                    scientificMode = calculatorScientificMode,
                                    onScientificModeChange = { calculatorScientificMode = it }
                                )
                            }
                            BuiltInTool.CALENDAR -> CalendarTool(
                                context = context,
                                month = calendarMonth,
                                onMonthChange = { calendarMonth = it },
                                modifier = Modifier.fillMaxSize()
                            )
                            BuiltInTool.UNIT_CONVERTER -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                UnitConverterTool(
                                    Modifier.fillMaxSize(),
                                    converterSearchQuery
                                )
                            }
                        }
                    }
            }
}
