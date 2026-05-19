package pl.polsatgranie.smarmegane.domain.signal

import java.util.Locale
import kotlin.math.abs

sealed class SignalValue {
    data class Bool(val value: Boolean) : SignalValue()
    data class Number(val value: Double, val unit: String? = null) : SignalValue()
    data class Enum(val code: Int, val label: String) : SignalValue()
}

fun SignalValue.displayText(): String =
    when (this) {
        is SignalValue.Bool -> if (value) "On" else "Off"
        is SignalValue.Enum -> label
        is SignalValue.Number -> {
            val absValue = abs(value)
            val base = if (absValue % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                String.format(Locale.US, "%.2f", value)
            }
            if (unit.isNullOrBlank()) base else "$base $unit"
        }
    }
