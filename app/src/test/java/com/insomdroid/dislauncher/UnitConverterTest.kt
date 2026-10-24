package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test
    fun convertsLengthUsingBaseUnitFactors() {
        val kilometre = ConverterUnit("Kilometre", "km", 1000.0)
        val metre = ConverterUnit("Metre", "m", 1.0)
        val centimetre = ConverterUnit("Centimetre", "cm", 0.01)

        assertEquals(1000.0, convertUnitValue(1.0, kilometre, metre), 0.000001)
        assertEquals(100.0, convertUnitValue(1.0, metre, centimetre), 0.000001)
        assertEquals(0.001, convertUnitValue(1.0, metre, kilometre), 0.000001)
    }

    @Test
    fun convertsTemperatureAcrossCelsiusFahrenheitAndKelvin() {
        val celsius = ConverterUnit("Celsius", "°C", 1.0, 273.15)
        val fahrenheit = ConverterUnit("Fahrenheit", "°F", 5.0 / 9.0, 459.67)
        val kelvin = ConverterUnit("Kelvin", "K", 1.0)

        assertEquals(32.0, convertUnitValue(0.0, celsius, fahrenheit), 0.000001)
        assertEquals(100.0, convertUnitValue(212.0, fahrenheit, celsius), 0.000001)
        assertEquals(0.0, convertUnitValue(273.15, kelvin, celsius), 0.000001)
        assertEquals(273.15, convertUnitValue(0.0, celsius, kelvin), 0.000001)
    }

    @Test
    fun formatsResultsWithoutUnnecessaryTrailingZeros() {
        assertEquals("0", formatConvertedValue(0.0))
        assertEquals("12.5", formatConvertedValue(12.50000000))
        assertEquals("0.33333333", formatConvertedValue(1.0 / 3.0))
        assertEquals("—", formatConvertedValue(Double.NaN))
        assertEquals("—", formatConvertedValue(Double.POSITIVE_INFINITY))
    }
}
