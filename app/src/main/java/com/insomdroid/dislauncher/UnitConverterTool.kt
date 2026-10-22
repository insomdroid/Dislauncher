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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
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
    val offset: Double = 0.0
)

private data class ConverterCategory(
    val name: String,
    val units: List<ConverterUnit>
)

private val converterCategories = listOf(
    ConverterCategory(builtInToolText("Length", "Panjang"), listOf(
        ConverterUnit("Millimeter", "mm", 0.001),
        ConverterUnit("Centimeter", "cm", 0.01),
        ConverterUnit("Meter", "m", 1.0),
        ConverterUnit("Kilometer", "km", 1000.0),
        ConverterUnit("Inch", "in", 0.0254),
        ConverterUnit("Foot", "ft", 0.3048),
        ConverterUnit("Mile", "mi", 1609.344)
    )),
    ConverterCategory(builtInToolText("Mass", "Massa"), listOf(
        ConverterUnit("Milligram", "mg", 0.001),
        ConverterUnit("Gram", "g", 1.0),
        ConverterUnit("Kilogram", "kg", 1000.0),
        ConverterUnit("Metric ton", "t", 1_000_000.0),
        ConverterUnit("Pound", "lb", 453.59237),
        ConverterUnit("Ounce", "oz", 28.349523125)
    )),
    ConverterCategory(builtInToolText("Temperature", "Suhu"), listOf(
        ConverterUnit("Celsius", "°C", 1.0, 273.15),
        ConverterUnit("Fahrenheit", "°F", 5.0 / 9.0, 459.67),
        ConverterUnit("Kelvin", "K", 1.0)
    )),
    ConverterCategory(builtInToolText("Speed", "Kecepatan"), listOf(
        ConverterUnit("Meter per second", "m/s", 1.0),
        ConverterUnit("Kilometer per hour", "km/h", 1.0 / 3.6),
        ConverterUnit("Mile per hour", "mph", 0.44704),
        ConverterUnit("Knot", "kn", 0.514444444444)
    )),
    ConverterCategory(builtInToolText("Area", "Luas"), listOf(
        ConverterUnit("Square centimeter", "cm²", 0.0001),
        ConverterUnit("Square meter", "m²", 1.0),
        ConverterUnit("Square kilometer", "km²", 1_000_000.0),
        ConverterUnit("Hectare", "ha", 10_000.0),
        ConverterUnit("Acre", "ac", 4046.8564224)
    )),
    ConverterCategory(builtInToolText("Volume", "Volume"), listOf(
        ConverterUnit("Milliliter", "mL", 0.001),
        ConverterUnit("Liter", "L", 1.0),
        ConverterUnit("Cubic meter", "m³", 1000.0),
        ConverterUnit("US gallon", "gal", 3.785411784),
        ConverterUnit("US cup", "cup", 0.2365882365)
    )),
    ConverterCategory(builtInToolText("Time", "Waktu"), listOf(
        ConverterUnit("Millisecond", "ms", 0.001),
        ConverterUnit("Second", "s", 1.0),
        ConverterUnit("Minute", "min", 60.0),
        ConverterUnit("Hour", "h", 3600.0),
        ConverterUnit("Day", "d", 86400.0)
    ))
)

private fun convertUnitValue(value: Double, from: ConverterUnit, to: ConverterUnit): Double {
    val baseValue = (value + from.offset) * from.factor
    return baseValue / to.factor - to.offset
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
    var input by remember { mutableStateOf("1") }
    var fromIndex by remember { mutableStateOf(0) }
    var toIndex by remember { mutableStateOf(1) }
    val category = converterCategories[categoryIndex]
    val fromUnit = category.units[fromIndex.coerceIn(category.units.indices)]
    val toUnit = category.units[toIndex.coerceIn(category.units.indices)]
    val numericInput = input.replace(',', '.').toDoubleOrNull()
    val result = numericInput?.let { formatConvertedValue(convertUnitValue(it, fromUnit, toUnit)) }.orEmpty()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                builtInToolText("Category", "Kategori"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ConverterDropdown(
                label = category.name,
                options = converterCategories.map { it.name },
                onSelected = { selected ->
                    categoryIndex = selected
                    fromIndex = 0
                    toIndex = if (converterCategories[selected].units.size > 1) 1 else 0
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                builtInToolText("Result", "Hasil"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
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
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    }
                )
            }
        }
    }
}
