package dev.wonderdesk.shared

import kotlin.math.*

/** Bounded recursive-descent evaluator. No eval(), scripting, reflection or I/O. */
object Expression {
    fun evaluate(source: String): Double {
        require(source.length in 1..512) { "Enter an expression of at most 512 characters" }
        class Parser {
            var at = 0
            var depth = 0
            fun skip() { while (at < source.length && source[at].isWhitespace()) at++ }
            fun take(c: Char): Boolean { skip(); return if (at < source.length && source[at] == c) { at++; true } else false }
            fun sum(): Double {
                var n = product()
                while (true) n = when { take('+') -> n + product(); take('-') -> n - product(); else -> return n }
            }
            fun product(): Double {
                var n = unary()
                while (true) n = when {
                    take('*') -> n * unary()
                    take('/') -> { val d = unary(); require(d != 0.0) { "Cannot divide by zero" }; n / d }
                    take('%') -> { val d = unary(); require(d != 0.0) { "Cannot divide by zero" }; n % d }
                    else -> return n
                }
            }
            fun unary(): Double {
                require(++depth < 64) { "Expression is too deeply nested" }
                val result = when { take('+') -> unary(); take('-') -> -unary(); else -> power() }
                depth--
                return result
            }
            fun power(): Double { val n = atom(); return if (take('^')) n.pow(unary()) else n }
            fun atom(): Double {
                if (take('(')) { val n = sum(); require(take(')')) { "Missing closing parenthesis" }; return n }
                skip()
                if (at < source.length && source[at].isLetter()) {
                    val begin = at
                    while (at < source.length && source[at].isLetter()) at++
                    val name = source.substring(begin, at).lowercase()
                    if (name == "pi") return PI
                    if (name == "e") return E
                    require(take('(')) { "Use $name(value)" }
                    val n = sum(); require(take(')')) { "Missing closing parenthesis" }
                    return when (name) {
                        "sin" -> sin(n); "cos" -> cos(n); "tan" -> tan(n)
                        "sqrt" -> sqrt(n); "ln" -> ln(n); "log" -> log10(n); "abs" -> abs(n)
                        else -> error("Unknown function: $name")
                    }
                }
                val begin = at
                while (at < source.length && (source[at].isDigit() || source[at] == '.')) at++
                return source.substring(begin, at).toDoubleOrNull() ?: error("Expected a number at position ${at + 1}")
            }
        }
        val parser = Parser()
        val result = parser.sum()
        parser.skip()
        require(parser.at == source.length) { "Unexpected input at position ${parser.at + 1}" }
        require(result.isFinite()) { "Result is outside the real finite range" }
        return result
    }

    fun format(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
