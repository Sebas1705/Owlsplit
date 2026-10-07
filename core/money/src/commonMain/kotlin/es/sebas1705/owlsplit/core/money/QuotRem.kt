package es.sebas1705.owlsplit.core.money

/** Result of [mulDivRem]: quotient truncated toward zero and the remainder with the sign of the product. */
internal data class QuotRem(val quotient: Long, val remainder: Long)
