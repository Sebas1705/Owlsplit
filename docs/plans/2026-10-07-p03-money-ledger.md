# P3 · Dinero y cálculo de saldos — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implementar en Kotlin común puro las reglas de dinero de [DOMAIN.md](../DOMAIN.md): divisas, importes en unidades mínimas, reparto por restos mayores, tipos de cambio en punto fijo, saldos que suman exactamente cero y simplificación voraz de deudas.

**Architecture:** Nuevo módulo `core:money` (tipos de valor y aritmética exacta, sin dependencias) y paquete `domain.ledger` en `domain:usecases` (cálculo de partes, netos por gasto, saldos y pagos sugeridos). Todo son funciones puras sobre tipos inmutables; P4 mapeará las entidades de la base de datos a estos tipos.

**Tech Stack:** Kotlin Multiplatform (solo `commonMain`), `kotlin-test`.

**Platform:** KMP (código común; los tests corren en la JVM de Android con `testDebugUnitTest`)

**Depende de:** P1 terminado. Independiente de P2.

---

## Contexto para quien ejecuta

- Mismo entorno que P1. Repo `~/Documents/Repos/Owlsplit`.
- Reglas de negocio: [docs/DOMAIN.md](../DOMAIN.md) y [ADR 0005](../adr/0005-money-as-minor-units.md). Si algo de este plan las contradice, manda `DOMAIN.md` y hay que avisar.
- Prohibido `Float`/`Double` en todo este código.
- Ruta base del código: `es/sebas1705/owlsplit/...`.
- Comandos de test:
  - `./gradlew :core:money:testDebugUnitTest`
  - `./gradlew :domain:usecases:testDebugUnitTest`
  - Un test concreto: añadir `--tests "es.sebas1705.owlsplit.core.money.AllocationTest"`.

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Modificar | `settings.gradle.kts`, `domain/usecases/build.gradle.kts`, `MODULE_MAP.md`, `docs/plans/README.md` |
| Crear | `core/money/build.gradle.kts` |
| Crear | `core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/`: `Currency.kt`, `CurrencyTable.kt`, `Money.kt`, `LongMath.kt`, `Allocation.kt`, `FxRate.kt`, `Conversion.kt` |
| Test | `core/money/src/commonTest/kotlin/es/sebas1705/owlsplit/core/money/`: `CurrencyTest.kt`, `MoneyTest.kt`, `LongMathTest.kt`, `AllocationTest.kt`, `FxRateTest.kt`, `ConversionTest.kt` |
| Crear | `domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/ledger/`: `LedgerModel.kt`, `ShareCalculator.kt`, `NetCalculator.kt`, `BalanceCalculator.kt`, `DebtSimplifier.kt` |
| Test | `domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/ledger/`: `ShareCalculatorTest.kt`, `NetCalculatorTest.kt`, `BalanceCalculatorTest.kt`, `DebtSimplifierTest.kt`, `LedgerPropertiesTest.kt`, `Fixtures.kt` |

---

### Task 1: Módulo `core:money` y divisas

**Files:**
- Create: `core/money/build.gradle.kts`, `Currency.kt`, `CurrencyTable.kt`
- Modify: `settings.gradle.kts`
- Test: `CurrencyTest.kt`

- [ ] **Step 1: Crear el módulo**

`core/money/build.gradle.kts`:
```kotlin
plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```
En `settings.gradle.kts`, dentro del `include(` de core, añadir `":core:money",` después de `":core:logging",`.

- [ ] **Step 2: Escribir el test que falla**

`core/money/src/commonTest/kotlin/es/sebas1705/owlsplit/core/money/CurrencyTest.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrencyTest {
    @Test
    fun `known currencies carry their ISO 4217 decimals`() {
        assertEquals(2, Currency.of("EUR").decimals)
        assertEquals(0, Currency.of("JPY").decimals)
        assertEquals(3, Currency.of("KWD").decimals)
    }

    @Test
    fun `unknown currency is rejected`() {
        assertFailsWith<IllegalArgumentException> { Currency.of("XXX") }
        assertFalse(Currency.isKnown("XXX"))
        assertTrue(Currency.isKnown("USD"))
    }

    @Test
    fun `malformed codes are rejected`() {
        assertFailsWith<IllegalArgumentException> { Currency("eur", 2) }
        assertFailsWith<IllegalArgumentException> { Currency("EURO", 2) }
        assertFailsWith<IllegalArgumentException> { Currency("EUR", 4) }
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `./gradlew :core:money:testDebugUnitTest`
Expected: FAIL de compilación, `Unresolved reference 'Currency'`.

- [ ] **Step 4: Implementar**

`core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/Currency.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

/** ISO 4217 currency with its number of minor-unit digits (EUR 2, JPY 0, KWD 3). */
data class Currency(val code: String, val decimals: Int) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Invalid ISO 4217 code: $code" }
        require(decimals in 0..MAX_DECIMALS) { "Unsupported decimals $decimals for $code" }
    }

    companion object {
        const val MAX_DECIMALS = 3

        fun of(code: String): Currency =
            requireNotNull(CurrencyTable.byCode[code]) { "Unknown currency: $code" }

        fun isKnown(code: String): Boolean = code in CurrencyTable.byCode
    }
}
```

`core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/CurrencyTable.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

/** Currencies supported by the app, with their ISO 4217 minor-unit digits. */
internal object CurrencyTable {
    private val decimalsByCode = mapOf(
        "AED" to 2, "ARS" to 2, "AUD" to 2, "BGN" to 2, "BHD" to 3, "BRL" to 2, "CAD" to 2,
        "CHF" to 2, "CLP" to 0, "CNY" to 2, "COP" to 2, "CZK" to 2, "DKK" to 2, "EGP" to 2,
        "EUR" to 2, "GBP" to 2, "HKD" to 2, "HUF" to 2, "IDR" to 2, "ILS" to 2, "INR" to 2,
        "ISK" to 0, "JOD" to 3, "JPY" to 0, "KRW" to 0, "KWD" to 3, "MAD" to 2, "MXN" to 2,
        "MYR" to 2, "NOK" to 2, "NZD" to 2, "OMR" to 3, "PEN" to 2, "PHP" to 2, "PLN" to 2,
        "QAR" to 2, "RON" to 2, "RSD" to 2, "SAR" to 2, "SEK" to 2, "SGD" to 2, "THB" to 2,
        "TND" to 3, "TRY" to 2, "TWD" to 2, "UAH" to 2, "USD" to 2, "UYU" to 2, "VND" to 0,
        "ZAR" to 2,
    )

    val byCode: Map<String, Currency> = decimalsByCode.mapValues { (code, decimals) -> Currency(code, decimals) }
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `./gradlew :core:money:testDebugUnitTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts core/money
git commit -m "feat(money): add core:money module with ISO 4217 currencies"
```

### Task 2: `Money`

**Files:**
- Create: `Money.kt`
- Test: `MoneyTest.kt`

- [ ] **Step 1: Test**

`core/money/src/commonTest/kotlin/es/sebas1705/owlsplit/core/money/MoneyTest.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    private val eur = Currency.of("EUR")
    private val usd = Currency.of("USD")

    @Test
    fun `adds and subtracts in the same currency`() {
        assertEquals(Money(1500, eur), Money(1000, eur) + Money(500, eur))
        assertEquals(Money(-250, eur), Money(250, eur) - Money(500, eur))
        assertEquals(Money(-7, eur), -Money(7, eur))
        assertTrue(Money.zero(eur).isZero)
    }

    @Test
    fun `mixing currencies is a programming error`() {
        assertFailsWith<CurrencyMismatchException> { Money(1, eur) + Money(1, usd) }
    }

    @Test
    fun `amounts beyond the safe range are rejected`() {
        assertFailsWith<IllegalArgumentException> { Money(Money.MAX_ABS + 1, eur) }
        assertFailsWith<IllegalArgumentException> { Money(Money.MAX_ABS, eur) + Money(1, eur) }
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.MoneyTest"`
Expected: FAIL, `Unresolved reference 'Money'`.

- [ ] **Step 3: Implementar**

`core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/Money.kt`:
```kotlin
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
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.MoneyTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/money
git commit -m "feat(money): add Money value type"
```

### Task 3: Multiplicación-división exacta de 128 bits

**Files:**
- Create: `LongMath.kt`
- Test: `LongMathTest.kt`

- [ ] **Step 1: Test**

`core/money/src/commonTest/kotlin/es/sebas1705/owlsplit/core/money/LongMathTest.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LongMathTest {
    @Test
    fun `small values truncate toward zero with signed remainder`() {
        assertEquals(QuotRem(10, 1), mulDivRem(7, 3, 2))
        assertEquals(QuotRem(-10, -1), mulDivRem(-7, 3, 2))
        assertEquals(QuotRem(-10, -1), mulDivRem(7, -3, 2))
        assertEquals(QuotRem(10, 1), mulDivRem(-7, -3, 2))
        assertEquals(QuotRem(0, 0), mulDivRem(0, 123, 7))
    }

    @Test
    fun `products beyond 64 bits are exact`() {
        // 10^12 * 9*10^17 = 9*10^29, far beyond Long; divided by 10^12.
        assertEquals(
            QuotRem(900_000_000_000_000_000, 0),
            mulDivRem(1_000_000_000_000, 900_000_000_000_000_000, 1_000_000_000_000),
        )
        // (2^62 + 1) * 6 / 4 = 6917529027641081857 remainder 2.
        assertEquals(QuotRem(6_917_529_027_641_081_857, 2), mulDivRem((1L shl 62) + 1, 6, 4))
    }

    @Test
    fun `quotient overflow is reported`() {
        assertFailsWith<ArithmeticException> { mulDivRem(Long.MAX_VALUE, 2, 1) }
    }

    @Test
    fun `invalid arguments are rejected`() {
        assertFailsWith<IllegalArgumentException> { mulDivRem(1, 1, 0) }
        assertFailsWith<IllegalArgumentException> { mulDivRem(Long.MIN_VALUE, 1, 1) }
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.LongMathTest"`
Expected: FAIL, `Unresolved reference 'mulDivRem'`.

- [ ] **Step 3: Implementar**

`core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/LongMath.kt`:
```kotlin
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
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.LongMathTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add core/money
git commit -m "feat(money): exact 128-bit multiply-divide"
```

### Task 4: Reparto por restos mayores

**Files:**
- Create: `Allocation.kt`
- Test: `AllocationTest.kt`

- [ ] **Step 1: Test**

`core/money/src/commonTest/kotlin/es/sebas1705/owlsplit/core/money/AllocationTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.AllocationTest"`
Expected: FAIL, `Unresolved reference 'Allocation'`.

- [ ] **Step 3: Implementar**

`core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/Allocation.kt`:
```kotlin
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
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.AllocationTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add core/money
git commit -m "feat(money): largest-remainder allocation"
```

### Task 5: Tipo de cambio en punto fijo

**Files:**
- Create: `FxRate.kt`
- Test: `FxRateTest.kt`

- [ ] **Step 1: Test**

`core/money/src/commonTest/kotlin/es/sebas1705/owlsplit/core/money/FxRateTest.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FxRateTest {
    @Test
    fun `parses decimal strings with dot or comma`() {
        assertEquals(923_456_789L, FxRate.parse("0.923456789").scaled)
        assertEquals(1_500_000_000L, FxRate.parse("1,5").scaled)
        assertEquals(FxRate.IDENTITY, FxRate.parse("1"))
        assertEquals(163_500_000_000L, FxRate.parse("163.5").scaled)
    }

    @Test
    fun `rejects invalid rates`() {
        for (bad in listOf("", "abc", "0", "0.000000000", "-1", "1.1234567891", "1000000")) {
            assertFailsWith<IllegalArgumentException>(bad) { FxRate.parse(bad) }
        }
    }

    @Test
    fun `formats without trailing zeros`() {
        assertEquals("1", FxRate.IDENTITY.toPlainString())
        assertEquals("1.5", FxRate(1_500_000_000).toPlainString())
        assertEquals("0.923456789", FxRate(923_456_789).toPlainString())
        assertEquals("0.0061", FxRate(6_100_000).toPlainString())
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.FxRateTest"`
Expected: FAIL, `Unresolved reference 'FxRate'`.

- [ ] **Step 3: Implementar**

`core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/FxRate.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

import kotlin.jvm.JvmInline

/**
 * Units of the target currency per one major unit of the source currency,
 * as a fixed-point number with 9 decimals (`scaled / 10⁹`).
 */
@JvmInline
value class FxRate(val scaled: Long) {
    init {
        require(scaled in 1..MAX_SCALED) { "Rate out of range: $scaled" }
    }

    fun toPlainString(): String {
        val integer = scaled / SCALE
        val fraction = (scaled % SCALE).toString().padStart(DECIMALS, '0').trimEnd('0')
        return if (fraction.isEmpty()) integer.toString() else "$integer.$fraction"
    }

    companion object {
        const val DECIMALS = 9
        const val SCALE = 1_000_000_000L

        /** Highest supported rate: 900 000 (keeps conversions inside Long, see Conversion). */
        const val MAX_SCALED = 900_000L * SCALE

        val IDENTITY = FxRate(SCALE)

        private val pattern = Regex("""^(\d{1,6})(?:[.,](\d{1,9}))?$""")

        /** Parses "0.923456789" or "0,92". Rejects zero, negatives and more than 9 decimals. */
        fun parse(decimal: String): FxRate {
            val match = requireNotNull(pattern.matchEntire(decimal.trim())) { "Invalid rate: '$decimal'" }
            val integer = match.groupValues[1].toLong()
            val fraction = match.groupValues[2].padEnd(DECIMALS, '0').toLong()
            val scaled = integer * SCALE + fraction
            require(scaled > 0) { "Rate must be positive: '$decimal'" }
            return FxRate(scaled)
        }
    }
}
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.FxRateTest"`
Expected: PASS (3 tests). Nota: `"1000000"` falla porque el patrón admite como mucho 6 cifras enteras (máx. 999 999) y `MAX_SCALED` limita a 900 000.

- [ ] **Step 5: Commit**

```bash
git add core/money
git commit -m "feat(money): fixed-point exchange rate"
```

### Task 6: Conversión entre divisas

**Files:**
- Create: `Conversion.kt`
- Test: `ConversionTest.kt`

- [ ] **Step 1: Test**

`core/money/src/commonTest/kotlin/es/sebas1705/owlsplit/core/money/ConversionTest.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionTest {
    private val eur = Currency.of("EUR")
    private val usd = Currency.of("USD")
    private val jpy = Currency.of("JPY")
    private val kwd = Currency.of("KWD")

    @Test
    fun `same number of decimals`() {
        // 10.00 USD at 0.92 → 9.20 EUR
        assertEquals(920L, Conversion.convert(1000, usd, eur, FxRate.parse("0.92")).truncated)
    }

    @Test
    fun `from a currency without decimals`() {
        // 1000 JPY at 0.0061 → 6.10 EUR
        assertEquals(610L, Conversion.convert(1000, jpy, eur, FxRate.parse("0.0061")).truncated)
    }

    @Test
    fun `to a currency without decimals`() {
        // 10.00 EUR at 163.5 → 1635 JPY
        assertEquals(1635L, Conversion.convert(1000, eur, jpy, FxRate.parse("163.5")).truncated)
    }

    @Test
    fun `to a currency with three decimals`() {
        // 10.00 EUR at 0.33 → 3.300 KWD
        assertEquals(3300L, Conversion.convert(1000, eur, kwd, FxRate.parse("0.33")).truncated)
    }

    @Test
    fun `truncates toward zero and exposes the remainder`() {
        // 1 cent at 0.5 → 0.5 cents: truncated 0, remainder is half the denominator.
        val half = Conversion.convert(1, usd, eur, FxRate.parse("0.5"))
        assertEquals(0L, half.truncated)
        assertEquals(half.denominator / 2, half.remainder)

        val negative = Conversion.convert(-1, usd, eur, FxRate.parse("0.5"))
        assertEquals(0L, negative.truncated)
        assertEquals(-negative.denominator / 2, negative.remainder)
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :core:money:testDebugUnitTest --tests "es.sebas1705.owlsplit.core.money.ConversionTest"`
Expected: FAIL, `Unresolved reference 'Conversion'`.

- [ ] **Step 3: Implementar**

`core/money/src/commonMain/kotlin/es/sebas1705/owlsplit/core/money/Conversion.kt`:
```kotlin
package es.sebas1705.owlsplit.core.money

/**
 * A converted amount: [truncated] minor units (toward zero) plus [remainder] / [denominator]
 * of a unit. Conversions of one expense share the denominator, so remainders are comparable.
 */
data class Converted(val truncated: Long, val remainder: Long, val denominator: Long)

object Conversion {
    /** Converts [amountMinor] of [from] into minor units of [to] at [rate] (to per one unit of from). */
    fun convert(amountMinor: Long, from: Currency, to: Currency, rate: FxRate): Converted {
        // amount/10^dFrom major units × rate.scaled/10^9 × 10^dTo minor units of `to`.
        // rate.scaled ≤ 9·10^14 and 10^dTo ≤ 10^3, so the factor stays below 9.2·10^18.
        val factor = rate.scaled * pow10(to.decimals)
        val denominator = FxRate.SCALE * pow10(from.decimals)
        val qr = mulDivRem(amountMinor, factor, denominator)
        return Converted(qr.quotient, qr.remainder, denominator)
    }
}
```

- [ ] **Step 4: Ver que pasa y ejecutar todo el módulo**

Run: `./gradlew :core:money:testDebugUnitTest`
Expected: PASS (todos los tests de `core:money`, 25).

- [ ] **Step 5: Commit**

```bash
git add core/money
git commit -m "feat(money): currency conversion with exact remainders"
```

### Task 7: Modelo del libro de cuentas y cálculo de partes

**Files:**
- Modify: `domain/usecases/build.gradle.kts`
- Create: `LedgerModel.kt`, `ShareCalculator.kt`
- Test: `Fixtures.kt`, `ShareCalculatorTest.kt`

- [ ] **Step 1: Dependencias de `domain:usecases`**

`domain/usecases/build.gradle.kts`:
```kotlin
plugins { alias(libs.plugins.kmp.domain) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:money"))
            implementation(project(":core:common"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

- [ ] **Step 2: Fixtures de test**

`domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/ledger/Fixtures.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate

internal val EUR = Currency.of("EUR")
internal val USD = Currency.of("USD")
internal val JPY = Currency.of("JPY")

internal val ana = LedgerMember("ana", sortKey = 0)
internal val sebas = LedgerMember("sebas", sortKey = 1)
internal val marta = LedgerMember("marta", sortKey = 2)
internal val luis = LedgerMember("luis", sortKey = 3)
internal val four = listOf(ana, sebas, marta, luis)

internal fun equalAmong(vararg members: LedgerMember) =
    SplitSpec(SplitType.EQUAL, members.associate { it.id to 1L })

internal fun expense(
    amountMinor: Long,
    payers: Map<String, Long>,
    split: SplitSpec,
    currency: Currency = EUR,
    rate: FxRate = FxRate.IDENTITY,
    id: String = "e1",
) = ExpenseEntry(id, amountMinor, currency, rate, payers, split)
```

- [ ] **Step 3: Test de partes**

`domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/ledger/ShareCalculatorTest.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareCalculatorTest {
    private fun ok(result: SplitResult) = (result as SplitResult.Ok).shares
    private fun issue(result: SplitResult) = (result as SplitResult.Invalid).issue

    @Test
    fun `equal split follows member order, not map order`() {
        val split = SplitSpec(SplitType.EQUAL, mapOf("marta" to 1L, "ana" to 1L, "sebas" to 1L))
        val shares = ok(ShareCalculator.shares(1000, split, listOf(marta, sebas, ana)))
        assertEquals(mapOf("ana" to 334L, "sebas" to 333L, "marta" to 333L), shares)
    }

    @Test
    fun `members with value zero are left out of an equal split`() {
        val split = SplitSpec(SplitType.EQUAL, mapOf("ana" to 1L, "sebas" to 0L, "marta" to 1L))
        assertEquals(mapOf("ana" to 500L, "marta" to 500L), ok(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `weights`() {
        val split = SplitSpec(SplitType.WEIGHTS, mapOf("ana" to 2L, "sebas" to 1L))
        assertEquals(mapOf("ana" to 667L, "sebas" to 333L), ok(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `percentages in hundredths`() {
        val split = SplitSpec(SplitType.PERCENT, mapOf("ana" to 7_500L, "sebas" to 2_500L))
        assertEquals(mapOf("ana" to 750L, "sebas" to 250L), ok(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `percentages must add up to 100`() {
        val split = SplitSpec(SplitType.PERCENT, mapOf("ana" to 5_000L, "sebas" to 4_000L))
        assertEquals(SplitIssue.PercentSumMismatch(1_000), issue(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `exact amounts`() {
        val split = SplitSpec(SplitType.EXACT, mapOf("ana" to 1_000L, "luis" to 3_860L))
        assertEquals(mapOf("ana" to 1_000L, "luis" to 3_860L), ok(ShareCalculator.shares(4_860, split, four)))
    }

    @Test
    fun `exact amounts report what is missing`() {
        val split = SplitSpec(SplitType.EXACT, mapOf("ana" to 1_000L, "luis" to 3_540L))
        assertEquals(SplitIssue.ExactSumMismatch(320), issue(ShareCalculator.shares(4_860, split, four)))
    }

    @Test
    fun `invalid splits`() {
        assertEquals(
            SplitIssue.NoBeneficiary,
            issue(ShareCalculator.shares(1000, SplitSpec(SplitType.EQUAL, mapOf("ana" to 0L)), four)),
        )
        assertEquals(
            SplitIssue.UnknownMember("zoe"),
            issue(ShareCalculator.shares(1000, SplitSpec(SplitType.EQUAL, mapOf("zoe" to 1L)), four)),
        )
        assertEquals(
            SplitIssue.NegativeValue,
            issue(ShareCalculator.shares(1000, SplitSpec(SplitType.WEIGHTS, mapOf("ana" to -1L)), four)),
        )
        assertEquals(SplitIssue.AmountOutOfRange, issue(ShareCalculator.shares(0, equalAmong(ana), four)))
    }
}
```

- [ ] **Step 4: Ver que falla**

Run: `./gradlew :domain:usecases:testDebugUnitTest`
Expected: FAIL, `Unresolved reference 'LedgerMember'`.

- [ ] **Step 5: Modelo**

`domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/ledger/LedgerModel.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate

/** A group member as the ledger sees it. [sortKey] then [id] give the deterministic order. */
data class LedgerMember(val id: String, val sortKey: Long)

internal val memberOrder: Comparator<LedgerMember> = compareBy({ it.sortKey }, { it.id })

enum class SplitType { EQUAL, WEIGHTS, EXACT, PERCENT }

/**
 * How an expense is shared. Members with a value > 0 take part.
 * EQUAL: the value only marks participation. WEIGHTS: integer weights.
 * EXACT: minor units. PERCENT: hundredths of a percent (10 000 = 100 %).
 */
data class SplitSpec(val type: SplitType, val values: Map<String, Long>)

data class ExpenseEntry(
    val id: String,
    val amountMinor: Long,
    val currency: Currency,
    /** Base-currency units per one unit of [currency]; ignored when [currency] is the base. */
    val fxRateToBase: FxRate,
    val payers: Map<String, Long>,
    val split: SplitSpec,
)

data class TransferEntry(
    val id: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: Currency,
    val fxRateToBase: FxRate,
)

data class SuggestedPayment(val fromMemberId: String, val toMemberId: String, val amountMinor: Long)

sealed interface SplitIssue {
    data object AmountOutOfRange : SplitIssue
    data object NoBeneficiary : SplitIssue
    data object NegativeValue : SplitIssue
    data object SameMember : SplitIssue
    data class UnknownMember(val memberId: String) : SplitIssue

    /** Positive: still missing; negative: too much. */
    data class ExactSumMismatch(val missingMinor: Long) : SplitIssue
    data class PercentSumMismatch(val missingHundredths: Long) : SplitIssue
    data class PayersSumMismatch(val missingMinor: Long) : SplitIssue
}

sealed interface SplitResult {
    data class Ok(val shares: Map<String, Long>) : SplitResult
    data class Invalid(val issue: SplitIssue) : SplitResult
}

class InvalidEntryException(val entryId: String, val issue: SplitIssue) :
    IllegalArgumentException("Entry $entryId is invalid: $issue")
```

- [ ] **Step 6: Calculadora de partes**

`domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/ledger/ShareCalculator.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Allocation
import es.sebas1705.owlsplit.core.money.Money

/** What each member owes for one expense, in the expense currency. See docs/DOMAIN.md#reparto. */
object ShareCalculator {
    const val FULL_PERCENT = 10_000L

    fun shares(amountMinor: Long, split: SplitSpec, members: List<LedgerMember>): SplitResult {
        if (amountMinor !in 1..Money.MAX_EXPENSE_MINOR) return SplitResult.Invalid(SplitIssue.AmountOutOfRange)
        val knownIds = members.mapTo(HashSet()) { it.id }
        split.values.keys.firstOrNull { it !in knownIds }?.let {
            return SplitResult.Invalid(SplitIssue.UnknownMember(it))
        }
        if (split.values.values.any { it < 0 }) return SplitResult.Invalid(SplitIssue.NegativeValue)

        val included = members.filter { (split.values[it.id] ?: 0L) > 0L }.sortedWith(memberOrder)
        if (included.isEmpty()) return SplitResult.Invalid(SplitIssue.NoBeneficiary)
        val value = { member: LedgerMember -> split.values.getValue(member.id) }

        return when (split.type) {
            SplitType.EQUAL -> SplitResult.Ok(allocate(amountMinor, included) { 1L })
            SplitType.WEIGHTS -> SplitResult.Ok(allocate(amountMinor, included, value))
            SplitType.PERCENT -> {
                val sum = included.sumOf(value)
                if (sum != FULL_PERCENT) {
                    SplitResult.Invalid(SplitIssue.PercentSumMismatch(FULL_PERCENT - sum))
                } else {
                    SplitResult.Ok(allocate(amountMinor, included, value))
                }
            }
            SplitType.EXACT -> {
                val sum = included.sumOf(value)
                if (sum != amountMinor) {
                    SplitResult.Invalid(SplitIssue.ExactSumMismatch(amountMinor - sum))
                } else {
                    SplitResult.Ok(included.associate { it.id to value(it) })
                }
            }
        }
    }

    private fun allocate(
        total: Long,
        ordered: List<LedgerMember>,
        weight: (LedgerMember) -> Long,
    ): Map<String, Long> =
        ordered.zip(Allocation.byWeights(total, ordered.map(weight)))
            .associate { (member, part) -> member.id to part }
}
```

- [ ] **Step 7: Ver que pasa**

Run: `./gradlew :domain:usecases:testDebugUnitTest`
Expected: PASS (8 tests).

- [ ] **Step 8: Commit**

```bash
git add domain/usecases
git commit -m "feat(ledger): split shares by equal, weights, percent and exact"
```

### Task 8: Neto por gasto y transferencia en la divisa base

**Files:**
- Create: `NetCalculator.kt`
- Test: `NetCalculatorTest.kt`

- [ ] **Step 1: Test**

`domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/ledger/NetCalculatorTest.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.FxRate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NetCalculatorTest {
    @Test
    fun `one payer, equal split, base currency`() {
        val e = expense(4_860, payers = mapOf("ana" to 4_860L), split = equalAmong(ana, sebas, marta, luis))
        assertEquals(
            mapOf("ana" to 3_645L, "sebas" to -1_215L, "marta" to -1_215L, "luis" to -1_215L),
            NetCalculator.expenseNet(e, four, EUR),
        )
    }

    @Test
    fun `several payers`() {
        val e = expense(
            4_860,
            payers = mapOf("ana" to 3_000L, "sebas" to 1_860L),
            split = equalAmong(ana, sebas, marta, luis),
        )
        assertEquals(
            mapOf("ana" to 1_785L, "sebas" to 645L, "marta" to -1_215L, "luis" to -1_215L),
            NetCalculator.expenseNet(e, four, EUR),
        )
    }

    @Test
    fun `payers must add up to the amount`() {
        val e = expense(4_860, payers = mapOf("ana" to 4_000L), split = equalAmong(ana, sebas))
        val error = assertFailsWith<InvalidEntryException> { NetCalculator.expenseNet(e, four, EUR) }
        assertEquals(SplitIssue.PayersSumMismatch(860), error.issue)
    }

    @Test
    fun `an invalid split is reported with the entry id`() {
        val e = expense(100, payers = mapOf("ana" to 100L), split = equalAmong(), id = "bad")
        val error = assertFailsWith<InvalidEntryException> { NetCalculator.expenseNet(e, four, EUR) }
        assertEquals("bad", error.entryId)
        assertEquals(SplitIssue.NoBeneficiary, error.issue)
    }

    @Test
    fun `foreign currency converts to nets that still sum to zero`() {
        val rate = FxRate.parse("0.923456789")
        val e = expense(1_000, payers = mapOf("ana" to 1_000L), split = equalAmong(ana, sebas, marta), USD, rate)
        val net = NetCalculator.expenseNet(e, four, EUR)
        assertEquals(0L, net.values.sum())
        // Exact values: ana +666 USD cents → 615.02 EUR cents; sebas/marta −333/−333 USD cents → −307.51.
        assertTrue(abs(net.getValue("ana") - 615) <= 1)
        assertTrue(abs(net.getValue("sebas") + 308) <= 1)
        assertTrue(abs(net.getValue("marta") + 308) <= 1)
    }

    @Test
    fun `currency without decimals into euros`() {
        val e = expense(
            3_000,
            payers = mapOf("ana" to 3_000L),
            split = equalAmong(ana, sebas, marta),
            currency = JPY,
            rate = FxRate.parse("0.0061"),
        )
        // 3000 JPY = 18.30 EUR: ana is owed 2/3 = 12.20, the others owe 6.10 each.
        assertEquals(
            mapOf("ana" to 1_220L, "sebas" to -610L, "marta" to -610L),
            NetCalculator.expenseNet(e, four, EUR),
        )
    }

    @Test
    fun `transfer credits the payer and debits the receiver`() {
        val t = TransferEntry("t1", "luis", "ana", 4_650, EUR, FxRate.IDENTITY)
        assertEquals(mapOf("luis" to 4_650L, "ana" to -4_650L), NetCalculator.transferNet(t, four, EUR))
    }

    @Test
    fun `transfer to oneself is invalid`() {
        val t = TransferEntry("t1", "ana", "ana", 100, EUR, FxRate.IDENTITY)
        val error = assertFailsWith<InvalidEntryException> { NetCalculator.transferNet(t, four, EUR) }
        assertEquals(SplitIssue.SameMember, error.issue)
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "es.sebas1705.owlsplit.domain.ledger.NetCalculatorTest"`
Expected: FAIL, `Unresolved reference 'NetCalculator'`.

- [ ] **Step 3: Implementar**

`domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/ledger/NetCalculator.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.money.Money
import kotlin.math.abs

/**
 * Effect of one entry on each member's balance, in the group base currency.
 * Positive = the member is owed money. The values of one entry always sum to exactly 0.
 * See docs/DOMAIN.md#divisas.
 */
object NetCalculator {

    fun expenseNet(expense: ExpenseEntry, members: List<LedgerMember>, base: Currency): Map<String, Long> {
        val shares = when (val result = ShareCalculator.shares(expense.amountMinor, expense.split, members)) {
            is SplitResult.Ok -> result.shares
            is SplitResult.Invalid -> throw InvalidEntryException(expense.id, result.issue)
        }
        validatePayers(expense, members)

        val net = HashMap<String, Long>()
        expense.payers.forEach { (id, paid) -> net[id] = (net[id] ?: 0L) + paid }
        shares.forEach { (id, share) -> net[id] = (net[id] ?: 0L) - share }
        return toBase(net, expense.currency, base, expense.fxRateToBase, members)
    }

    fun transferNet(transfer: TransferEntry, members: List<LedgerMember>, base: Currency): Map<String, Long> {
        val knownIds = members.mapTo(HashSet()) { it.id }
        val issue = when {
            transfer.amountMinor !in 1..Money.MAX_EXPENSE_MINOR -> SplitIssue.AmountOutOfRange
            transfer.fromMemberId == transfer.toMemberId -> SplitIssue.SameMember
            transfer.fromMemberId !in knownIds -> SplitIssue.UnknownMember(transfer.fromMemberId)
            transfer.toMemberId !in knownIds -> SplitIssue.UnknownMember(transfer.toMemberId)
            else -> null
        }
        if (issue != null) throw InvalidEntryException(transfer.id, issue)

        val net = mapOf(transfer.fromMemberId to transfer.amountMinor, transfer.toMemberId to -transfer.amountMinor)
        return toBase(net, transfer.currency, base, transfer.fxRateToBase, members)
    }

    private fun validatePayers(expense: ExpenseEntry, members: List<LedgerMember>) {
        val knownIds = members.mapTo(HashSet()) { it.id }
        expense.payers.keys.firstOrNull { it !in knownIds }?.let {
            throw InvalidEntryException(expense.id, SplitIssue.UnknownMember(it))
        }
        if (expense.payers.values.any { it < 0 }) throw InvalidEntryException(expense.id, SplitIssue.NegativeValue)
        val paid = expense.payers.values.sum()
        if (paid != expense.amountMinor) {
            throw InvalidEntryException(expense.id, SplitIssue.PayersSumMismatch(expense.amountMinor - paid))
        }
    }

    /**
     * Converts each member's net (which sum to 0) to the base currency, truncating toward zero,
     * then hands out or takes back the leftover units by largest remainder so the result
     * still sums to exactly 0. Ties follow member order.
     */
    private fun toBase(
        net: Map<String, Long>,
        from: Currency,
        base: Currency,
        rate: FxRate,
        members: List<LedgerMember>,
    ): Map<String, Long> {
        if (from == base) return net
        val ordered = members.sortedWith(memberOrder).map { it.id }.filter { it in net }
        val converted = ordered.associateWith { Conversion.convert(net.getValue(it), from, base, rate) }
        val result = converted.mapValuesTo(LinkedHashMap()) { it.value.truncated }

        val leftover = -result.values.sum()
        if (leftover != 0L) {
            val sign = if (leftover > 0) 1L else -1L
            ordered.sortedByDescending { sign * converted.getValue(it).remainder }
                .take(abs(leftover).toInt())
                .forEach { result[it] = result.getValue(it) + sign }
        }
        return result
    }
}
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "es.sebas1705.owlsplit.domain.ledger.NetCalculatorTest"`
Expected: PASS (8 tests). Si `foreign currency …` falla en los valores aproximados pero la suma es 0, revisar el signo del reparto del sobrante: con `leftover > 0` se suma a los restos positivos más altos.

- [ ] **Step 5: Commit**

```bash
git add domain/usecases
git commit -m "feat(ledger): per-entry nets in base currency summing to zero"
```

### Task 9: Saldos del grupo

**Files:**
- Create: `BalanceCalculator.kt`
- Test: `BalanceCalculatorTest.kt`

- [ ] **Step 1: Test**

`domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/ledger/BalanceCalculatorTest.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.FxRate
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceCalculatorTest {
    @Test
    fun `sums expenses and transfers per member`() {
        val dinner = expense(4_860, mapOf("sebas" to 4_860L), equalAmong(ana, sebas, marta, luis), id = "dinner")
        val taxi = expense(2_000, mapOf("ana" to 2_000L), equalAmong(ana, luis), id = "taxi")
        val payback = TransferEntry("t1", "luis", "sebas", 1_215, EUR, FxRate.IDENTITY)

        val balances = BalanceCalculator.balances(four, listOf(dinner, taxi), listOf(payback), EUR)

        assertEquals(
            mapOf("ana" to -215L, "sebas" to 2_430L, "marta" to -1_215L, "luis" to -1_000L),
            balances,
        )
        assertEquals(0L, balances.values.sum())
    }

    @Test
    fun `members without activity have a zero balance`() {
        val e = expense(1_000, mapOf("ana" to 1_000L), equalAmong(ana, sebas))
        assertEquals(0L, BalanceCalculator.balances(four, listOf(e), emptyList(), EUR).getValue("luis"))
    }

    @Test
    fun `empty group`() {
        assertEquals(
            mapOf("ana" to 0L, "sebas" to 0L, "marta" to 0L, "luis" to 0L),
            BalanceCalculator.balances(four, emptyList(), emptyList(), EUR),
        )
    }
}
```
(Comprobación del primer caso: cena 48,60 € pagada por Sebas → Sebas +36,45 y el resto −12,15; taxi 20 € de Ana entre Ana y Luis → Ana +10 y Luis −10; Luis devuelve 12,15 € a Sebas → Luis +12,15 y Sebas −12,15. Total: Ana −12,15 + 10 = −2,15; Sebas 36,45 − 12,15 = 24,30; Marta −12,15; Luis −12,15 − 10 + 12,15 = −10.)

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "es.sebas1705.owlsplit.domain.ledger.BalanceCalculatorTest"`
Expected: FAIL, `Unresolved reference 'BalanceCalculator'`.

- [ ] **Step 3: Implementar**

`domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/ledger/BalanceCalculator.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Currency

/** Net balance of every member in the base currency. Positive = is owed. Sums to exactly 0. */
object BalanceCalculator {
    fun balances(
        members: List<LedgerMember>,
        expenses: List<ExpenseEntry>,
        transfers: List<TransferEntry>,
        base: Currency,
    ): Map<String, Long> {
        val totals = members.sortedWith(memberOrder).associateTo(LinkedHashMap()) { it.id to 0L }
        val nets = expenses.map { NetCalculator.expenseNet(it, members, base) } +
            transfers.map { NetCalculator.transferNet(it, members, base) }
        nets.forEach { net -> net.forEach { (id, value) -> totals[id] = totals.getValue(id) + value } }
        check(totals.values.sum() == 0L) { "Balances must sum to zero, got ${totals.values.sum()}" }
        return totals
    }
}
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "es.sebas1705.owlsplit.domain.ledger.BalanceCalculatorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add domain/usecases
git commit -m "feat(ledger): group balances"
```

### Task 10: Simplificación de deudas

**Files:**
- Create: `DebtSimplifier.kt`
- Test: `DebtSimplifierTest.kt`

- [ ] **Step 1: Test**

`domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/ledger/DebtSimplifierTest.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DebtSimplifierTest {
    @Test
    fun `largest debtor pays largest creditor first`() {
        val balances = mapOf("ana" to 5_820L, "sebas" to 2_350L, "marta" to -1_170L, "luis" to -7_000L)
        assertEquals(
            listOf(
                SuggestedPayment("luis", "ana", 5_820),
                SuggestedPayment("luis", "sebas", 1_180),
                SuggestedPayment("marta", "sebas", 1_170),
            ),
            DebtSimplifier.simplify(balances, four),
        )
    }

    @Test
    fun `ties follow member order`() {
        val balances = mapOf("ana" to 100L, "sebas" to 100L, "marta" to -100L, "luis" to -100L)
        assertEquals(
            listOf(SuggestedPayment("marta", "ana", 100), SuggestedPayment("luis", "sebas", 100)),
            DebtSimplifier.simplify(balances, four),
        )
    }

    @Test
    fun `nothing to settle`() {
        assertTrue(DebtSimplifier.simplify(mapOf("ana" to 0L, "sebas" to 0L), four).isEmpty())
    }

    @Test
    fun `balances must sum to zero`() {
        assertFailsWith<IllegalArgumentException> { DebtSimplifier.simplify(mapOf("ana" to 1L), four) }
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "es.sebas1705.owlsplit.domain.ledger.DebtSimplifierTest"`
Expected: FAIL, `Unresolved reference 'DebtSimplifier'`.

- [ ] **Step 3: Implementar**

`domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/ledger/DebtSimplifier.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

/**
 * Greedy settlement (as in Settle Up): the largest debtor pays the largest creditor until
 * everyone is at zero. At most n − 1 payments; ties follow member order.
 * See docs/DOMAIN.md#simplificación-de-deudas.
 */
object DebtSimplifier {
    fun simplify(balances: Map<String, Long>, members: List<LedgerMember>): List<SuggestedPayment> {
        require(balances.values.sum() == 0L) { "Balances must sum to zero" }
        val rank = members.sortedWith(memberOrder).withIndex().associate { (index, member) -> member.id to index }
        require(balances.keys.all { it in rank }) { "Every balance must belong to a known member" }

        val byAmountThenRank = compareByDescending<Position> { it.amount }.thenBy { rank.getValue(it.memberId) }
        val creditors = balances.filter { it.value > 0 }.map { Position(it.key, it.value) }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { Position(it.key, -it.value) }.toMutableList()

        val payments = mutableListOf<SuggestedPayment>()
        while (debtors.isNotEmpty()) {
            debtors.sortWith(byAmountThenRank)
            creditors.sortWith(byAmountThenRank)
            val debtor = debtors.first()
            val creditor = creditors.first()
            val amount = minOf(debtor.amount, creditor.amount)
            payments += SuggestedPayment(debtor.memberId, creditor.memberId, amount)
            debtor.amount -= amount
            creditor.amount -= amount
            if (debtor.amount == 0L) debtors.removeAt(0)
            if (creditor.amount == 0L) creditors.removeAt(0)
        }
        return payments
    }

    private class Position(val memberId: String, var amount: Long)
}
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "es.sebas1705.owlsplit.domain.ledger.DebtSimplifierTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add domain/usecases
git commit -m "feat(ledger): greedy debt simplification"
```

### Task 11: Tests de propiedades

**Files:**
- Test: `LedgerPropertiesTest.kt`

Se generan grupos aleatorios con semilla fija (reproducibles) y se comprueban los invariantes de DOMAIN.md. No se añade ninguna librería: basta `kotlin.random.Random` con semilla.

- [ ] **Step 1: Test**

`domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/ledger/LedgerPropertiesTest.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Allocation
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerPropertiesTest {

    private data class Scenario(
        val members: List<LedgerMember>,
        val expenses: List<ExpenseEntry>,
        val transfers: List<TransferEntry>,
    )

    private val foreign = listOf(
        Currency.of("USD") to FxRate.parse("0.923456789"),
        Currency.of("JPY") to FxRate.parse("0.0061"),
        Currency.of("KWD") to FxRate.parse("2.987654321"),
    )

    private fun scenario(random: Random): Scenario {
        val members = List(random.nextInt(2, 9)) { LedgerMember("m$it", sortKey = random.nextLong(0, 5)) }
        val expenses = List(random.nextInt(1, 31)) { index ->
            val (currency, rate) = if (random.nextInt(3) == 0) foreign.random(random) else EUR to FxRate.IDENTITY
            val amount = random.nextLong(1, 500_000)
            val payers = members.shuffled(random).take(random.nextInt(1, 3))
            val paid = Allocation.byWeights(amount, payers.map { random.nextLong(1, 5) })
            val included = members.filter { random.nextBoolean() }.ifEmpty { listOf(members.first()) }
            val split = when (SplitType.entries.random(random)) {
                SplitType.EQUAL -> SplitSpec(SplitType.EQUAL, included.associate { it.id to 1L })
                SplitType.WEIGHTS -> SplitSpec(SplitType.WEIGHTS, included.associate { it.id to random.nextLong(1, 6) })
                SplitType.PERCENT -> SplitSpec(
                    SplitType.PERCENT,
                    included.zip(Allocation.byWeights(10_000, included.map { random.nextLong(1, 10) }))
                        .associate { (m, p) -> m.id to p },
                )
                SplitType.EXACT -> SplitSpec(
                    SplitType.EXACT,
                    included.zip(Allocation.byWeights(amount, included.map { random.nextLong(1, 10) }))
                        .associate { (m, p) -> m.id to p },
                )
            }
            ExpenseEntry("e$index", amount, currency, rate, payers.zip(paid).associate { (m, p) -> m.id to p }, split)
        }
        val transfers = List(random.nextInt(0, 4)) { index ->
            val (from, to) = members.shuffled(random).take(2)
            TransferEntry("t$index", from.id, to.id, random.nextLong(1, 100_000), EUR, FxRate.IDENTITY)
        }
        return Scenario(members, expenses, transfers)
    }

    private fun forEachScenario(block: (Scenario, Random) -> Unit) {
        repeat(500) { seed ->
            val random = Random(seed)
            block(scenario(random), random)
        }
    }

    @Test
    fun `every entry nets to zero and so do the balances`() = forEachScenario { s, _ ->
        s.expenses.forEach { assertEquals(0L, NetCalculator.expenseNet(it, s.members, EUR).values.sum(), it.id) }
        assertEquals(0L, BalanceCalculator.balances(s.members, s.expenses, s.transfers, EUR).values.sum())
    }

    @Test
    fun `results do not depend on input order`() = forEachScenario { s, random ->
        val reference = BalanceCalculator.balances(s.members, s.expenses, s.transfers, EUR)
        val shuffled = BalanceCalculator.balances(
            s.members.shuffled(random),
            s.expenses.shuffled(random),
            s.transfers.shuffled(random),
            EUR,
        )
        assertEquals(reference, shuffled)
        assertEquals(
            DebtSimplifier.simplify(reference, s.members),
            DebtSimplifier.simplify(reference, s.members.shuffled(random)),
        )
    }

    @Test
    fun `suggested payments settle everyone in at most n - 1 steps`() = forEachScenario { s, _ ->
        val balances = BalanceCalculator.balances(s.members, s.expenses, s.transfers, EUR)
        val payments = DebtSimplifier.simplify(balances, s.members)
        val after = balances.toMutableMap()
        payments.forEach {
            assertTrue(it.amountMinor > 0)
            after[it.fromMemberId] = after.getValue(it.fromMemberId) + it.amountMinor
            after[it.toMemberId] = after.getValue(it.toMemberId) - it.amountMinor
        }
        assertTrue(after.values.all { it == 0L }, "Not settled: $after")
        assertTrue(payments.size <= s.members.size - 1)
    }
}
```

- [ ] **Step 2: Ejecutar**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "es.sebas1705.owlsplit.domain.ledger.LedgerPropertiesTest"`
Expected: PASS (3 tests × 500 escenarios). Si alguno falla, el mensaje incluye el id del gasto; reproducir con la semilla (índice del `repeat`) y añadir ese caso como test de tabla en el archivo correspondiente **antes** de corregir.

- [ ] **Step 3: Commit**

```bash
git add domain/usecases
git commit -m "test(ledger): property tests for zero-sum, order independence and settlement"
```

### Task 12: Verificación y documentación

- [ ] **Step 1: Todo el proyecto**

```bash
./gradlew detekt testDebugUnitTest :composeApp:assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`. Ajustes de Detekt previstos (en `config/detekt/detekt.yml`, nunca con `@Suppress`):
- `MagicNumber` en los tests: añadir `excludes: ['**/commonTest/**']` a esa regla. Si lo marca en código de producción, extraer constantes con nombre.
- `ReturnCount` en `ShareCalculator.shares` (validaciones con retorno temprano): poner `excludeGuardClauses: true` en esa regla. Si sigue saltando, subir `max` a 4; las validaciones en cadena son intencionadas.
- `CurrencyTable` puede disparar `MagicNumber` por los decimales: añadir `ignorePropertyDeclaration: true` a `MagicNumber`.

- [ ] **Step 2: Documentación**

- `MODULE_MAP.md`: en la fila de `core:money`, quitar "(se crea en P3)"; en `domain:usecases`, dejar "Casos de uso; `ledger`: partes, netos, saldos y pagos sugeridos".
- `docs/plans/README.md`: estado de P3 → `Hecho`.

- [ ] **Step 3: Commit y push**

```bash
git add MODULE_MAP.md docs/plans/README.md config/detekt/detekt.yml
git commit -m "docs: mark P3 as done"
git push
```

## Verificación final de P3

- `./gradlew :core:money:testDebugUnitTest :domain:usecases:testDebugUnitTest` → todos en verde (25 + 26 tests, 1 500 escenarios aleatorios).
- `grep -rn "Double\|Float" core/money/src domain/usecases/src/commonMain` → sin resultados.
- CI verde tras el push.

## Fuera de este plan

- Modo "sin simplificar" de Cómo saldar (opcional según el spec): se decide al hacer la pantalla en P9; necesitaría deudas por pares a partir de los netos de cada gasto.
- Mapeo de entidades de la base de datos a `ExpenseEntry`/`TransferEntry`: P4.
