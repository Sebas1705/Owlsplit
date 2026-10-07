package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AllocationTest {
    @Test
    fun `ten euros among three gives the extra cent to the first`() {
        assertEquals(listOf(334L, 333L, 333L), Allocation.equally(1000, 3))
    }

    @Test
    fun `leftover goes to the largest remainder`() {
        // 100 * 1/3 = 33 r1, 100 * 2/3 = 66 r2 → the extra unit goes to index 1.
        assertEquals(listOf(33L, 67L), Allocation.byWeights(100, listOf(1, 2)))
    }

    @Test
    fun `ties go to the earlier index`() {
        assertEquals(listOf(1L, 1L, 0L), Allocation.equally(2, 3))
    }

    @Test
    fun `zero weights get nothing`() {
        assertEquals(listOf(0L, 10L, 0L), Allocation.byWeights(10, listOf(0, 1, 0)))
    }

    @Test
    fun `zero total gives zeros`() {
        assertEquals(listOf(0L, 0L), Allocation.equally(0, 2))
    }

    @Test
    fun `parts always sum to the total`() {
        val weights = listOf(3L, 7L, 11L, 13L, 1L)
        for (total in listOf(1L, 99L, 12_345L, 999_999_999_999L)) {
            assertEquals(total, Allocation.byWeights(total, weights).sum())
        }
    }

    @Test
    fun `invalid input is rejected`() {
        assertFailsWith<IllegalArgumentException> { Allocation.byWeights(-1, listOf(1)) }
        assertFailsWith<IllegalArgumentException> { Allocation.byWeights(1, emptyList()) }
        assertFailsWith<IllegalArgumentException> { Allocation.byWeights(1, listOf(0, 0)) }
        assertFailsWith<IllegalArgumentException> { Allocation.byWeights(1, listOf(-1, 2)) }
    }
}
