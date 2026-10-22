package dev.budgettracker.domain

import java.math.BigDecimal

/** Values are in minor units (fils / centavos). No floating point accounting. */
enum class Currency { AED, PHP }

data class Money(val currency: Currency, val minorUnits: Long) {
    init { require(minorUnits >= 0L) { "Negative amount" } }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot combine AED and PHP" }
        return Money(currency, Math.addExact(minorUnits, other.minorUnits))
    }

    /** Signed difference, negative when actual spending exceeds the budget. */
    fun differenceFrom(other: Money): Long {
        require(currency == other.currency) { "Cannot compare AED and PHP" }
        return Math.subtractExact(minorUnits, other.minorUnits)
    }

    fun decimal(): BigDecimal = BigDecimal.valueOf(minorUnits, 2)

    companion object {
        fun zero(currency: Currency) = Money(currency, 0L)
    }
}
