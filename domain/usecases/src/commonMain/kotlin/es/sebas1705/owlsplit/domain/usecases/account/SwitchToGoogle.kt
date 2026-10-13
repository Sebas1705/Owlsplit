package es.sebas1705.owlsplit.domain.usecases.account

import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.PreviewResult
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.data.repository.sync.SyncControl
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.InviteResult
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.JoinResult
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import es.sebas1705.owlsplit.domain.usecases.invite.ShareInvite
import kotlinx.coroutines.flow.first

/** A group this device's account takes along when it gives way to another one: its code and my member. */
data class CarriedGroup(val groupId: String, val code: String, val memberId: String?)

/**
 * Takes this device's groups from one account to another with what already exists: leave (my
 * member stays, abandoned, with its expenses), then join with the code and claim that same member
 * (docs/designs/2026-10-12-identidad-miembros.md, option A).
 */
class CarryGroups(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val accounts: AccountService,
    private val share: ShareInvite,
    private val preview: PreviewInvite,
    private val join: JoinGroup,
    private val claim: ClaimMember,
) {
    /** Leaves every group of the current account; null (and nothing left) if a code cannot be published. */
    suspend fun leaveAll(): List<CarriedGroup>? {
        val uid = accounts.currentSession()?.uid ?: return null
        val carried = groups.observeGroups().first().filter { uid in it.memberUids }.map { group ->
            val code = (share(group.id) as? InviteResult.Ready)?.link?.code ?: return null
            val member = members.observeMembers(group.id).first().firstOrNull { it.userUid == uid }
            CarriedGroup(group.id, code, member?.id)
        }
        carried.forEach { groups.leave(it.groupId) }
        return carried
    }

    /** Joins the current account to [carried] and claims the member each one had. Best effort. */
    suspend fun rejoinAll(carried: List<CarriedGroup>) {
        carried.forEach { group ->
            val found = preview(group.code) as? PreviewResult.Found ?: return@forEach
            if (join(found.preview) is JoinResult.Joined) claim(group.groupId, group.memberId)
        }
    }
}

/**
 * "Link Google" found an account that already has Owlsplit data (a new install that started without
 * signing in): sign in to it instead. Its groups come down with the sync; the groups made here
 * meanwhile go along with it. If signing in fails, they are rejoined by the account that left them.
 */
class SwitchToGoogle(
    private val google: GoogleCredentials,
    private val accounts: AccountService,
    private val profiles: ProfileRepository,
    private val sync: SyncControl,
    private val carry: CarryGroups,
) {
    suspend operator fun invoke(): GoogleOutcome {
        val local = profiles.profile.first()
        var carried = emptyList<CarriedGroup>()
        val outcome = google.withToken { idToken, accessToken ->
            // Everything this account wrote must be on the server before it gives way.
            if (!sync.flush()) return@withToken AccountResult.Offline
            carried = carry.leaveAll() ?: return@withToken AccountResult.Offline
            if (!sync.flush()) return@withToken AccountResult.Offline
            accounts.signInWithGoogle(idToken, accessToken)
        }
        if (outcome !is GoogleOutcome.Done) {
            carry.rejoinAll(carried)
            return outcome
        }
        val restored = profiles.restore()
        (restored ?: local)?.let { profiles.save(it) }
        carry.rejoinAll(carried)
        return GoogleOutcome.Done(restored)
    }
}
