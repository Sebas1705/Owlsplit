package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.models.TransferInput
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferRepository
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.usecases.balances.GroupBalances
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Today in the device time zone: the date of new expenses and transfers. */
fun interface Today {
    operator fun invoke(): LocalDate

    companion object {
        val System = Today { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    }
}

/** My member in a group: the one linked to my user. */
fun List<Member>.mine(uid: String?): Member? = uid?.let { me -> firstOrNull { it.userUid == me } }

data class HomeGroup(
    val group: Group,
    val activeMembers: Int,
    /** My balance in the group currency; null when I have no member there. */
    val myBalance: Long?,
    /** Last thing that happened in the group (creation of its newest expense or transfer, or the group). */
    val lastActivity: Long,
)

data class HomeData(
    val groups: List<HomeGroup>,
    val preferredCurrency: String,
    /** Sum of my balances in groups whose currency is [preferredCurrency]. */
    val total: Long,
    /** My balances in groups of other currencies, per currency (converted in P9b). */
    val otherCurrencies: Map<String, Long>,
)

/** Inicio: my groups (not archived by me), most recent activity first, and my total balance. */
class ObserveHome(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val transfers: TransferRepository,
    private val balances: ObserveGroupBalances,
    private val accounts: AccountService,
    private val profiles: ProfileRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<HomeData> =
        combine(groups.observeGroups(), accounts.session, profiles.profile) { list, session, profile ->
            Triple(list.filter { session == null || session.uid !in it.archivedBy }, session?.uid, profile?.currency)
        }.flatMapLatest { (list, uid, currency) ->
            val preferred = currency ?: DEFAULT_CURRENCY
            if (list.isEmpty()) return@flatMapLatest flowOf(HomeData(emptyList(), preferred, 0, emptyMap()))
            combine(list.map { homeGroup(it, uid) }) { items ->
                val sorted = items.sortedByDescending { it.lastActivity }
                val byCurrency = sorted.filter { it.myBalance != null }
                    .groupBy({ it.group.baseCurrency }, { it.myBalance!! })
                    .mapValues { (_, values) -> values.sum() }
                HomeData(sorted, preferred, byCurrency[preferred] ?: 0, byCurrency - preferred)
            }
        }

    private fun homeGroup(group: Group, uid: String?): Flow<HomeGroup> = combine(
        members.observeMembers(group.id),
        expenses.observeExpenses(group.id),
        transfers.observeTransfers(group.id),
        balances(group.id),
    ) { memberList, expenseList, transferList, groupBalances ->
        val mine = memberList.mine(uid)
        HomeGroup(
            group = group,
            activeMembers = memberList.count { it.active },
            myBalance = mine?.let { groupBalances?.balances?.get(it.id) ?: 0 },
            lastActivity = maxOf(
                group.createdAt,
                expenseList.maxOfOrNull { it.createdAt } ?: 0,
                transferList.maxOfOrNull { it.createdAt } ?: 0,
            ),
        )
    }

    private companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

data class GroupDetail(
    val group: Group,
    /** Not deleted, in display order (deactivated ones included: they keep their balance). */
    val members: List<Member>,
    val expenses: List<Expense>,
    val transfers: List<Transfer>,
    val balances: GroupBalances?,
    val myMemberId: String?,
    val myUid: String?,
) {
    val archivedByMe: Boolean get() = myUid != null && myUid in group.archivedBy
}

class ObserveGroupDetail(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val transfers: TransferRepository,
    private val balances: ObserveGroupBalances,
    private val accounts: AccountService,
) {
    /** Null once the group is gone (left, or never synced here). */
    operator fun invoke(groupId: String): Flow<GroupDetail?> = combine(
        combine(groups.observeGroup(groupId), members.observeMembers(groupId), accounts.session, ::Triple),
        expenses.observeExpenses(groupId),
        transfers.observeTransfers(groupId),
        balances(groupId),
    ) { (group, memberList, session), expenseList, transferList, groupBalances ->
        group?.let {
            val uid = session?.uid
            GroupDetail(it, memberList, expenseList, transferList, groupBalances, memberList.mine(uid)?.id, uid)
        }
    }
}

/** Creating and editing groups. The creator joins with the name of their profile. */
class GroupActions(private val groups: GroupRepository, private val profiles: ProfileRepository) {
    suspend fun create(input: GroupInput): String =
        groups.create(input.normalised(), profiles.profile.first()?.name ?: DEFAULT_NAME)

    suspend fun update(id: String, input: GroupInput) = groups.update(id, input.normalised())

    suspend fun setArchived(id: String, archived: Boolean) = groups.setArchived(id, archived)

    suspend fun leave(id: String) = groups.leave(id)

    private fun GroupInput.normalised() = copy(name = name.trim().take(MAX_NAME))

    companion object {
        const val MAX_NAME = 60
        private const val DEFAULT_NAME = "Yo"
    }
}

/** Ghost members of a group. My own member cannot be deactivated or deleted. */
class MemberActions(private val members: MemberRepository) {
    suspend fun add(groupId: String, name: String, weight: Long = 1): String =
        members.add(groupId, MemberInput(name.trim().take(MAX_NAME), defaultWeight = weight.coerceIn(1, MAX_WEIGHT)))

    suspend fun update(member: Member, name: String, weight: Long) = members.update(
        member.id,
        MemberInput(name.trim().take(MAX_NAME), member.avatar, weight.coerceIn(1, MAX_WEIGHT)),
    )

    suspend fun setActive(member: Member, active: Boolean, myUid: String?) {
        require(active || member.userUid == null || member.userUid != myUid) { "Cannot deactivate yourself" }
        members.setActive(member.id, active)
    }

    /** False when the member has movements: deactivate it instead. */
    suspend fun delete(member: Member, myUid: String?): Boolean {
        require(member.userUid == null || member.userUid != myUid) { "Cannot delete yourself" }
        return members.deleteIfUnused(member.id)
    }

    companion object {
        const val MAX_NAME = 40
        const val MAX_WEIGHT = 100L
    }
}

/** One tap on a suggested payment: the transfer that settles it, in the group currency, today. */
class SettleUp(private val transfers: TransferRepository, private val today: Today = Today.System) {
    suspend operator fun invoke(groupId: String, payment: SuggestedPayment, currency: String): String =
        transfers.create(
            groupId,
            TransferInput(
                fromMemberId = payment.fromMemberId,
                toMemberId = payment.toMemberId,
                amountMinor = payment.amountMinor,
                currency = currency,
                fxRateScaled = FxRate.SCALE,
                date = today(),
            ),
        )
}

/** Payers' names for display, from member ids. */
fun List<Member>.nameOf(id: String): String = firstOrNull { it.id == id }?.name.orEmpty()
