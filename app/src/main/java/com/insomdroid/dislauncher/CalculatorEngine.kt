package com.insomdroid.dislauncher

import java.util.Locale

/** Pure expression parsing and formatting, isolated from the calculator UI. */
internal fun applyCalculatorPercentage(expression: String): String {
    var depth = 0
    var lastOperator = -1
    for (index in expression.indices) {
        when (expression[index]) {
            '(' -> depth++
            ')' -> depth--
            '+', '-', '*', '/' -> {
                val previous = expression.getOrNull(index - 1)
                if (
                    depth == 0 && index > 0 &&
                    previous != null && previous !in charArrayOf('+', '-', '*', '/', '(')
                ) {
                    lastOperator = index
                }
            }
        }
    }

    if (lastOperator <= 0) return "($expression)/100"
    val left = expression.substring(0, lastOperator)
    val operator = expression[lastOperator]
    val right = expression.substring(lastOperator + 1)
    return when (operator) {
        '+', '-' -> "$left$operator(($left)*($right)/100)"
        '*' -> "$left*(($right)/100)"
        '/' -> "$left/(($right)/100)"
        else -> "($expression)/100"
    }
}

internal fun formatCalculation(value: Double): String =
    if (!value.isFinite()) "Cannot calculate"
    else if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.')

internal class ExpressionParser(raw: String) {
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
