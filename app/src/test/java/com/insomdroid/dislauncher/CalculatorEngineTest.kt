package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalculatorEngineTest {
    @Test
    fun respectsArithmeticPrecedence() {
        assertEquals(14.0, ExpressionParser("2+3*4").parse(), 0.0000001)
        assertEquals(20.0, ExpressionParser("(2+3)*4").parse(), 0.0000001)
    }

    @Test
    fun evaluatesRightAssociativePowersAndUnarySigns() {
        assertEquals(512.0, ExpressionParser("2^3^2").parse(), 0.0000001)
        assertEquals(-8.0, ExpressionParser("-2^3").parse(), 0.0000001)
    }

    @Test
    fun evaluatesSupportedFunctionsAndConstants() {
        assertEquals(1.0, ExpressionParser("sin(90)").parse(), 0.0000001)
        assertEquals(3.0, ExpressionParser("sqrt(9)").parse(), 0.0000001)
        assertEquals(Math.PI, ExpressionParser("pi").parse(), 0.0000001)
        assertEquals(Math.E, ExpressionParser("e").parse(), 0.0000001)
    }

    @Test
    fun acceptsCalculatorDisplayOperators() {
        assertEquals(7.0, ExpressionParser("3×4−5").parse(), 0.0000001)
        assertEquals(2.0, ExpressionParser("8÷4").parse(), 0.0000001)
    }

    @Test
    fun rejectsInvalidExpressionsAndNonFiniteResults() {
        listOf("", "2/0", "sqrt(-1)", "ln(0)", "1..2", "2+").forEach { expression ->
            assertThrows(IllegalArgumentException::class.java) {
                ExpressionParser(expression).parse()
            }
        }
    }

    @Test
    fun expandsPercentageAccordingToTheLastTopLevelOperator() {
        assertEquals("200+((200)*(10)/100)", applyCalculatorPercentage("200+10"))
        assertEquals("200*((10)/100)", applyCalculatorPercentage("200*10"))
        assertEquals("(10)/100", applyCalculatorPercentage("10"))
        assertEquals("((2+(3*4)))/100", applyCalculatorPercentage("(2+(3*4))"))
    }

    @Test
    fun formatsFiniteAndNonFiniteResults() {
        assertEquals("12", formatCalculation(12.0))
        assertEquals("1.25", formatCalculation(1.25))
        assertEquals("Cannot calculate", formatCalculation(Double.POSITIVE_INFINITY))
        assertEquals("Cannot calculate", formatCalculation(Double.NaN))
    }
}
