package es.sebas1705.owlsplit.core.money

import kotlin.math.abs

internal data class QuotRem(val quotient: Long, val remainder: Long)

private const val LOW_32_MASK = 0xFFFF_FFFFuL
private const val HALF_BITS = 32
private const val LONG_BITS = 64
private const val SIGN_BIT = 63
private const val TOP_BIT_128 = 127
private const val MAX_POW10 = 18
private const val TEN = 10L

/**
 * Exact `(a * b) / c` truncated toward zero, through a 128-bit intermediate product.
 * The remainder carries the sign of `a * b`. Requires `c > 0` and `a, b != Long.MIN_VALUE`.
 *
 * @throws ArithmeticException if the quotient does not fit in a Long.
 */
internal fun mulDivRem(a: Long, b: Long, c: Long): QuotRem {
    require(c > 0) { "Divisor must be positive" }
    require(a != Long.MIN_VALUE && b != Long.MIN_VALUE) { "Long.MIN_VALUE is not supported" }
    val negative = (a < 0) != (b < 0)
    val x = abs(a).toULong()
    val y = abs(b).toULong()

    // 64 × 64 → 128-bit product as (hi, lo).
    val xl = x and LOW_32_MASK
    val xh = x shr HALF_BITS
    val yl = y and LOW_32_MASK
    val yh = y shr HALF_BITS
    val ll = xl * yl
    val lh = xl * yh
    val hl = xh * yl
    val hh = xh * yh
    val mid = (ll shr HALF_BITS) + (lh and LOW_32_MASK) + (hl and LOW_32_MASK)
    val lo = (ll and LOW_32_MASK) or (mid shl HALF_BITS)
    val hi = hh + (lh shr HALF_BITS) + (hl shr HALF_BITS) + (mid shr HALF_BITS)

    // Bitwise long division of the 128-bit value by c. rem < c < 2^63, so `rem shl 1` never overflows.
    val divisor = c.toULong()
    var rem = 0uL
    var quot = 0uL
    for (bitIndex in TOP_BIT_128 downTo 0) {
        val bit = if (bitIndex >= LONG_BITS) {
            (hi shr (bitIndex - LONG_BITS)) and 1uL
        } else {
            (lo shr bitIndex) and 1uL
        }
        rem = (rem shl 1) or bit
        if (rem >= divisor) {
            rem -= divisor
            if (bitIndex >= SIGN_BIT) throw ArithmeticException("Quotient overflows Long")
            quot = quot or (1uL shl bitIndex)
        }
    }
    val q = quot.toLong()
    val r = rem.toLong()
    return if (negative) QuotRem(-q, -r) else QuotRem(q, r)
}

internal fun pow10(exponent: Int): Long {
    require(exponent in 0..MAX_POW10) { "Exponent out of range: $exponent" }
    var result = 1L
    repeat(exponent) { result *= TEN }
    return result
}
