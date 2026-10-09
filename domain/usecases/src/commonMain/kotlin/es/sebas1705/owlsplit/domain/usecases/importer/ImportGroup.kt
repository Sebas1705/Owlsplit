package es.sebas1705.owlsplit.domain.usecases.importer

import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.TransferInput
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferRepository
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import kotlinx.coroutines.flow.first

/**
 * Creates a group from an import: me as the member I chose (or with my profile name), everyone else
 * as members without an account, then every expense and payment in date order. Entries in another
 * currency need its rate to the group's in [rates]; any without one are left out.
 */
class ImportGroup(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val transfers: TransferRepository,
    private val profiles: ProfileRepository,
) {
    data class Result(val groupId: String, val imported: Int, val leftOut: Int)

    @Suppress("LongParameterList") // What the person chose on the import screen.
    suspend operator fun invoke(
        parsed: ParsedImport,
        name: String,
        icon: String,
        accent: String,
        meIndex: Int?,
        rates: Map<String, FxRate>,
    ): Result {
        val base = parsed.mainCurrency
        val profile = profiles.profile.first()
        val myName = meIndex?.let { parsed.members[it] } ?: profile?.name ?: DEFAULT_NAME
        val groupId = groups.create(
            GroupInput(name.trim().take(GroupActions.MAX_NAME), icon, accent, base),
            myName.take(MAX_MEMBER_NAME),
        )
        val me = members.observeMembers(groupId).first().first().id
        profile?.payMethods?.orNull()?.let { members.publishMyPayMethods(it) }
        val ids = parsed.members.mapIndexed { index, member ->
            if (index == meIndex) me else members.add(groupId, MemberInput(member.take(MAX_MEMBER_NAME)))
        }
        var imported = 0
        parsed.entries.sortedBy { it.date }.forEach { entry ->
            val rate = if (entry.currency == base) FxRate.IDENTITY else rates[entry.currency] ?: return@forEach
            when (entry) {
                is ImportedEntry.Expense -> expenses.create(
                    groupId,
                    ExpenseInput(
                        title = entry.title.ifBlank { "—" },
                        category = entry.category,
                        note = null,
                        date = entry.date,
                        currency = entry.currency,
                        amountMinor = entry.amountMinor,
                        fxRateScaled = rate.scaled,
                        payers = entry.payers.mapKeys { ids[it.key] },
                        splitType = SplitType.EXACT,
                        splitValues = entry.shares.mapKeys { ids[it.key] },
                    ),
                )
                is ImportedEntry.Payment -> transfers.create(
                    groupId,
                    TransferInput(
                        ids[entry.from],
                        ids[entry.to],
                        entry.amountMinor,
                        entry.currency,
                        rate.scaled,
                        entry.date
                    ),
                )
            }
            imported++
        }
        return Result(groupId, imported, parsed.entries.size - imported + parsed.skipped)
    }

    private companion object {
        const val DEFAULT_NAME = "Yo"
        const val MAX_MEMBER_NAME = 40
    }
}
