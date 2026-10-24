package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CalculatorScientificTest {
    @Test
    fun supportsPowersAndParentheses() {
        assertEquals(512.0, ExpressionParser("2^3^2").parse(), 0.000001)
        assertEquals(14.0, ExpressionParser("2*(3+4)").parse(), 0.000001)
    }

    @Test
    fun supportsScientificFunctionsInDegrees() {
        assertEquals(0.5, ExpressionParser("sin(30)").parse(), 0.000001)
        assertEquals(1.0, ExpressionParser("cos(0)").parse(), 0.000001)
        assertEquals(4.0, ExpressionParser("sqrt(16)").parse(), 0.000001)
        assertEquals(2.0, ExpressionParser("log(100)").parse(), 0.000001)
        assertEquals(1.0, ExpressionParser("ln(e)").parse(), 0.000001)
    }

    @Test
    fun supportsPiConstantAndRejectsInvalidDomains() {
        assertEquals(PI, ExpressionParser("π").parse(), 0.000001)
        assertTrue(runCatching { ExpressionParser("sqrt(-1)").parse() }.isFailure)
        assertTrue(runCatching { ExpressionParser("log(0)").parse() }.isFailure)
    }
}
