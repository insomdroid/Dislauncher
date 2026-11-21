package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test
    fun convertsLengthUsingBaseUnitFactors() {
        val millimeter = ConverterUnit("Millimeter", "mm", 0.001)
        val meter = ConverterUnit("Meter", "m", 1.0)
        val kilometer = ConverterUnit("Kilometer", "km", 1000.0)
        val inch = ConverterUnit("Inch", "in", 0.0254)

        assertEquals(1.0, convertUnitValue(1000.0, millimeter, meter), 0.0000001)
        assertEquals(1.0, convertUnitValue(1000.0, meter, kilometer), 0.0000001)
        assertEquals(39.3700787, convertUnitValue(1.0, meter, inch), 0.0000001)
    }

    @Test
    fun convertsTemperatureWithOffsets() {
        val celsius = ConverterUnit("Celsius", "°C", 1.0, 273.15)
        val fahrenheit = ConverterUnit("Fahrenheit", "°F", 5.0 / 9.0, 459.67)
        val kelvin = ConverterUnit("Kelvin", "K", 1.0)

        assertEquals(0.0, convertUnitValue(32.0, fahrenheit, celsius), 0.0000001)
        assertEquals(32.0, convertUnitValue(0.0, celsius, fahrenheit), 0.0000001)
        assertEquals(273.15, convertUnitValue(0.0, celsius, kelvin), 0.0000001)
    }

    @Test
    fun formatsConvertedValuesWithoutUnnecessaryTrailingZeros() {
        assertEquals("12", formatConvertedValue(12.0))
        assertEquals("0.125", formatConvertedValue(0.125))
        assertEquals("0", formatConvertedValue(0.0))
        assertEquals("—", formatConvertedValue(Double.NaN))
        assertEquals("—", formatConvertedValue(Double.POSITIVE_INFINITY))
    }

    @Test
    fun roundsToEightDecimalPlaces() {
        assertEquals("1.12345679", formatConvertedValue(1.123456789))
    }
}
