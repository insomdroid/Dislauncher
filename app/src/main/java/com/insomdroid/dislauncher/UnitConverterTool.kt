package com.insomdroid.dislauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

private data class ConverterUnit(
    val name: String,
    val symbol: String,
    val factor: Double,
    val offset: Double = 0.0,
    val inverse: Boolean = false
)

private data class ConverterCategory(
    val name: String,
    val units: List<ConverterUnit>
)

private val converterCategories = listOf(
    ConverterCategory(builtInToolText("Length", "Panjang"), listOf(
        ConverterUnit(builtInToolText("Millimeter", "Milimeter"), "mm", 0.001),
        ConverterUnit(builtInToolText("Centimeter", "Sentimeter"), "cm", 0.01),
        ConverterUnit("Meter", "m", 1.0),
        ConverterUnit("Kilometer", "km", 1000.0),
        ConverterUnit(builtInToolText("Inch", "Inci"), "in", 0.0254),
        ConverterUnit(builtInToolText("Foot", "Kaki"), "ft", 0.3048),
        ConverterUnit(builtInToolText("Mile", "Mil"), "mi", 1609.344)
    )),
    ConverterCategory(builtInToolText("Mass", "Massa"), listOf(
        ConverterUnit(builtInToolText("Milligram", "Miligram"), "mg", 0.001),
        ConverterUnit("Gram", "g", 1.0),
        ConverterUnit("Kilogram", "kg", 1000.0),
        ConverterUnit(builtInToolText("Metric ton", "Ton metrik"), "t", 1_000_000.0),
        ConverterUnit(builtInToolText("Pound", "Pon"), "lb", 453.59237),
        ConverterUnit(builtInToolText("Ounce", "Ons"), "oz", 28.349523125)
    )),
    ConverterCategory(builtInToolText("Temperature", "Suhu"), listOf(
        ConverterUnit("Celsius", "°C", 1.0, 273.15),
        ConverterUnit("Fahrenheit", "°F", 5.0 / 9.0, 459.67),
        ConverterUnit("Kelvin", "K", 1.0)
    )),
    ConverterCategory(builtInToolText("Speed", "Kecepatan"), listOf(
        ConverterUnit(builtInToolText("Meter per second", "Meter per detik"), "m/s", 1.0),
        ConverterUnit(builtInToolText("Kilometer per hour", "Kilometer per jam"), "km/h", 1.0 / 3.6),
        ConverterUnit(builtInToolText("Mile per hour", "Mil per jam"), "mph", 0.44704),
        ConverterUnit("Knot", "kn", 0.514444444444)
    )),
    ConverterCategory(builtInToolText("Area", "Luas"), listOf(
        ConverterUnit(builtInToolText("Square centimeter", "Sentimeter persegi"), "cm²", 0.0001),
        ConverterUnit(builtInToolText("Square meter", "Meter persegi"), "m²", 1.0),
        ConverterUnit(builtInToolText("Square kilometer", "Kilometer persegi"), "km²", 1_000_000.0),
        ConverterUnit(builtInToolText("Hectare", "Hektare"), "ha", 10_000.0),
        ConverterUnit(builtInToolText("Acre", "Ekar"), "ac", 4046.8564224)
    )),
    ConverterCategory(builtInToolText("Volume", "Volume"), listOf(
        ConverterUnit("Milliliter", "mL", 0.001),
        ConverterUnit("Liter", "L", 1.0),
        ConverterUnit(builtInToolText("Cubic meter", "Meter kubik"), "m³", 1000.0),
        ConverterUnit(builtInToolText("US gallon", "Galon AS"), "gal", 3.785411784),
        ConverterUnit(builtInToolText("US cup", "Cangkir AS"), "cup", 0.2365882365)
    )),
    ConverterCategory(builtInToolText("Time", "Waktu"), listOf(
        ConverterUnit(builtInToolText("Millisecond", "Milidetik"), "ms", 0.001),
        ConverterUnit(builtInToolText("Second", "Detik"), "s", 1.0),
        ConverterUnit(builtInToolText("Minute", "Menit"), "min", 60.0),
        ConverterUnit(builtInToolText("Hour", "Jam"), "h", 3600.0),
        ConverterUnit(builtInToolText("Day", "Hari"), "d", 86400.0)
    )),
    ConverterCategory(builtInToolText("Energy", "Energi"), listOf(
        ConverterUnit("Joule", "J", 1.0),
        ConverterUnit("Kilojoule", "kJ", 1000.0),
        ConverterUnit("Calorie", "cal", 4.184),
        ConverterUnit("Kilocalorie", "kcal", 4184.0),
        ConverterUnit("Watt-hour", "Wh", 3600.0),
        ConverterUnit("Kilowatt-hour", "kWh", 3600000.0)
    )),
    ConverterCategory(builtInToolText("Power", "Daya"), listOf(
        ConverterUnit("Watt", "W", 1.0),
        ConverterUnit("Kilowatt", "kW", 1000.0),
        ConverterUnit("Metric horsepower", "hp", 735.49875),
        ConverterUnit("Mechanical horsepower", "hp (US)", 745.699872)
    )),
    ConverterCategory(builtInToolText("Data storage", "Penyimpanan data"), listOf(
        ConverterUnit("Byte", "B", 1.0),
        ConverterUnit("Kilobyte", "kB", 1000.0),
        ConverterUnit("Megabyte", "MB", 1000000.0),
        ConverterUnit("Gigabyte", "GB", 1000000000.0),
        ConverterUnit("Kibibyte", "KiB", 1024.0),
        ConverterUnit("Mebibyte", "MiB", 1048576.0),
        ConverterUnit("Gibibyte", "GiB", 1073741824.0)
    )),
)

private fun convertUnitValue(value: Double, from: ConverterUnit, to: ConverterUnit): Double {
    val baseValue = if (from.inverse) from.factor / value else (value + from.offset) * from.factor
    return if (to.inverse) to.factor / baseValue else baseValue / to.factor - to.offset
}

private fun formatConvertedValue(value: Double): String {
    if (!value.isFinite()) return "—"
    if (value == 0.0) return "0"
    return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
}

@Composable
internal fun UnitConverterTool(modifier: Modifier = Modifier) {
    var categoryIndex by remember { mutableStateOf(0) }
    var categoryQuery by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("1") }
    var fromIndex by remember { mutableStateOf(0) }
    var toIndex by remember { mutableStateOf(1) }
    val category = converterCategories[categoryIndex]
    val fromUnit = category.units[fromIndex.coerceIn(category.units.indices)]
    val toUnit = category.units[toIndex.coerceIn(category.units.indices)]
    val numericInput = input.replace(',', '.').toDoubleOrNull()
    val result = numericInput?.let { formatConvertedValue(convertUnitValue(it, fromUnit, toUnit)) }.orEmpty()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)
    ) {
        OutlinedTextField(
            value = categoryQuery,
            onValueChange = { categoryQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(builtInToolText("Search units and categories", "Cari satuan dan kategori")) },
            leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) }
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            converterCategories.forEachIndexed { index, item ->
                if (categoryQuery.isBlank() || item.name.contains(categoryQuery, ignoreCase = true) ||
                    item.units.any { it.name.contains(categoryQuery, ignoreCase = true) || it.symbol.contains(categoryQuery, ignoreCase = true) }) {
                FilterChip(
                    selected = categoryIndex == index,
                    onClick = {
                        categoryIndex = index
                        fromIndex = 0
                        toIndex = if (item.units.size > 1) 1 else 0
                    },
                    label = { Text(item.name, maxLines = 1) }
                )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.compact)) {
            Text(
                builtInToolText("From", "Dari"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = input,
                onValueChange = { next ->
                    if (next.isEmpty() || next.matches(Regex("[+-]?\\d*([.,]\\d*)?"))) input = next
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    ConverterDropdown(
                        label = fromUnit.symbol,
                        options = category.units.map { "${it.name} (${it.symbol})" },
                        onSelected = { fromIndex = it },
                        modifier = Modifier.width(112.dp)
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = {
                    val oldFrom = fromIndex
                    fromIndex = toIndex
                    toIndex = oldFrom
                },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    builtInToolText("Swap units", "Tukar satuan"),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.compact)) {
            Text(
                builtInToolText("Result", "Hasil"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (result.isNotBlank()) {
                        input = result
                        val oldFrom = fromIndex
                        fromIndex = toIndex
                        toIndex = oldFrom
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                    Text(
                        result.ifBlank { "—" },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        toUnit.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConverterDropdown(
    label: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(label) { mutableStateOf("") }
    val filteredOptions = options.withIndex().filter { (_, option) ->
        query.isBlank() || option.contains(query, ignoreCase = true)
    }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true; query = "" },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.width(240.dp).padding(horizontal = 8.dp),
                singleLine = true,
                placeholder = { Text(builtInToolText("Search unit", "Cari satuan")) }
            )
            filteredOptions.forEach { (index, option) ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        query = ""
                        onSelected(index)
                    }
                )
            }
        }
    }
}
