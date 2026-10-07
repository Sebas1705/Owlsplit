package es.sebas1705.owlsplit.core.money

/**
 * An amount in minor units (cents for EUR) of one currency. Never use floating point for money.
 *
 * [MAX_ABS] keeps every sum of a group far from Long overflow: a group has at most
 * 10 000 expenses of at most [MAX_EXPENSE_MINOR] each.
 */
data class Money(val amountMinor: Long, val currency: Currency) {
    init {
        require(amountMinor in -MAX_ABS..MAX_ABS) { "Amount out of range: $amountMinor" }
    }

    val isZero: Boolean get() = amountMinor == 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor + other.amountMinor, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor - other.amountMinor, currency)
    }

    operator fun unaryMinus(): Money = Money(-amountMinor, currency)

    private fun requireSameCurrency(other: Money) {
        if (other.currency != currency) throw CurrencyMismatchException(currency, other.currency)
    }

    companion object {
        /** Largest amount of a single expense or transfer: 10¹² minor units. */
        const val MAX_EXPENSE_MINOR = 1_000_000_000_000L

        /** Largest absolute value a [Money] may hold. */
        const val MAX_ABS = 100_000_000_000_000_000L

        fun zero(currency: Currency): Money = Money(0, currency)
    }
}

class CurrencyMismatchException(a: Currency, b: Currency) :
    IllegalArgumentException("Cannot combine ${a.code} with ${b.code}")
