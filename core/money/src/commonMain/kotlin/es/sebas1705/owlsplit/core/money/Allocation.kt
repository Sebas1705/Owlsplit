package es.sebas1705.owlsplit.core.money

/** Deterministic integer splitting (largest-remainder method). See docs/DOMAIN.md#reparto. */
object Allocation {
    /**
     * Splits [total] (≥ 0) proportionally to [weights] so the parts sum exactly to [total].
     * Leftover units go one by one to the largest fractional remainders; ties go to the
     * earlier index, so the result only depends on the order of [weights].
     */
    fun byWeights(total: Long, weights: List<Long>): List<Long> {
        require(total >= 0) { "Total must be non-negative" }
        require(weights.isNotEmpty()) { "At least one weight is required" }
        require(weights.all { it >= 0 }) { "Weights must be non-negative" }
        val weightSum = weights.sum()
        require(weightSum > 0) { "At least one weight must be positive" }

        val parts = LongArray(weights.size)
        val remainders = LongArray(weights.size)
        weights.forEachIndexed { index, weight ->
            val qr = mulDivRem(total, weight, weightSum)
            parts[index] = qr.quotient
            remainders[index] = qr.remainder
        }
        val leftover = total - parts.sum()
        weights.indices
            .sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
            .take(leftover.toInt())
            .forEach { parts[it]++ }
        return parts.toList()
    }

    fun equally(total: Long, count: Int): List<Long> = byWeights(total, List(count) { 1L })
}
