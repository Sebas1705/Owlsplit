package es.sebas1705.owlsplit.data.models

import kotlinx.datetime.LocalDate

// What the repositories take to create or edit a document (ids and sync metadata are theirs).

data class GroupInput(
    val name: String,
    val icon: String,
    val accent: String,
    val baseCurrency: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

data class MemberInput(val name: String, val avatar: String? = null, val defaultWeight: Long = 1)

data class ExpenseInput(
    val title: String,
    val category: ExpenseCategory,
    val note: String?,
    val date: LocalDate,
    val currency: String,
    val amountMinor: Long,
    val fxRateScaled: Long,
    val payers: Map<String, Long>,
    val splitType: SplitType,
    val splitValues: Map<String, Long>,
)

data class TransferInput(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateScaled: Long,
    val date: LocalDate,
    val note: String? = null,
)
