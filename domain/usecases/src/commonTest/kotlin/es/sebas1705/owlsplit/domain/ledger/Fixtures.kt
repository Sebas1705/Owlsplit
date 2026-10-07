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
