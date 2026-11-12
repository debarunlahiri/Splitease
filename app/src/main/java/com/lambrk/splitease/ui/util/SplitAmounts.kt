package com.lambrk.splitease.ui.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Each selected person's share of [total], computed the way the server does it
 * (see the backend's split calculator). People whose share can't be worked out yet are omitted.
 */
internal fun splitAmounts(
    total: String,
    type: String,
    people: List<String>,
    values: Map<String, String>,
): Map<String, BigDecimal> {
    val sum = total.toBigDecimalOrNull() ?: return emptyMap()
    if (people.isEmpty() || sum.signum() <= 0) return emptyMap()
    return when (type) {
        "EQUAL" -> {
            val base = sum.divide(BigDecimal(people.size), 2, RoundingMode.DOWN)
            val remainder = sum - base * BigDecimal(people.size)
            people.mapIndexed { i, id -> id to if (i == 0) base + remainder else base }.toMap()
        }
        "EXACT" -> people.mapNotNull { id ->
            values[id]?.toBigDecimalOrNull()?.let { id to it }
        }.toMap()
        "SHARES" -> {
            val units = people.map { values[it]?.toBigDecimalOrNull() ?: BigDecimal.ZERO }
            val sumUnits = units.fold(BigDecimal.ZERO) { a, b -> a + b }
            if (sumUnits.signum() <= 0) return emptyMap()
            val last = units.indexOfLast { it.signum() > 0 }
            var assigned = BigDecimal.ZERO
            people.mapIndexed { i, id ->
                val amount =
                    if (i == last) sum - assigned
                    else sum.multiply(units[i]).divide(sumUnits, 2, RoundingMode.HALF_UP)
                assigned += amount
                id to amount
            }.toMap()
        }
        else -> {
            val pct = people.map { values[it]?.toBigDecimalOrNull() }
            if (pct.any { it == null } || pct.sumOf { it!! }.compareTo(BigDecimal(100)) != 0)
                return people.mapNotNull { id ->
                    values[id]?.toBigDecimalOrNull()?.let {
                        id to sum.multiply(it).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                    }
                }.toMap()
            var assigned = BigDecimal.ZERO
            people.mapIndexed { i, id ->
                val amount =
                    if (i == people.lastIndex) sum - assigned
                    else sum.multiply(pct[i]!!).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                assigned += amount
                id to amount
            }.toMap()
        }
    }
}
