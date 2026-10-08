package es.sebas1705.owlsplit.domain.usecases.invite

import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.data.models.InvitePreview
import es.sebas1705.owlsplit.data.repository.invite.InviteService
import es.sebas1705.owlsplit.data.models.PreviewResult
import es.sebas1705.owlsplit.data.repository.sync.SyncControl
import es.sebas1705.owlsplit.domain.usecases.groups.mine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Invite codes: 8 characters without 0, O, 1 or I (see `InviteCodeGenerator`). */
object InviteCodes {
    private const val LENGTH = 8
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** What the user typed or pasted, or a whole link: uppercase, without spaces or dashes. */
    fun normalize(text: String): String =
        text.trim().substringAfterLast("/j/").uppercase().filter { it.isLetterOrDigit() }.take(LENGTH)

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** "ABCD EFGH": easier to read aloud. */
    fun display(code: String): String = code.chunked(LENGTH / 2).joinToString(" ")
}

data class InviteLink(val code: String, val url: String)

sealed interface InviteResult {
    data class Ready(val link: InviteLink) : InviteResult
    data object Offline : InviteResult
    data class Failed(val reason: String) : InviteResult
}

/**
 * The link to share: the group (with its current code) must be on the server, then
 * `invites/{code}` is published so others can preview it. [linkBase] is the Hosting site.
 */
class ShareInvite(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val invites: InviteService,
    private val sync: SyncControl,
    private val linkBase: String,
) {
    suspend operator fun invoke(groupId: String): InviteResult {
        if (!sync.flush()) return InviteResult.Offline
        val group = groups.observeGroup(groupId).first() ?: return InviteResult.Failed("Unknown group")
        val count = members.observeMembers(groupId).first().count { it.active }
        return when (val published = invites.publish(group, count)) {
            AccountResult.Done -> InviteResult.Ready(InviteLink(group.inviteCode, "$linkBase/j/${group.inviteCode}"))
            AccountResult.Offline -> InviteResult.Offline
            else -> InviteResult.Failed(published.toString())
        }
    }
}

/** A new code; the old one is withdrawn so its links stop working (3.8 of the spec). */
class RegenerateInvite(
    private val groups: GroupRepository,
    private val invites: InviteService,
    private val sync: SyncControl,
    private val share: ShareInvite,
) {
    suspend operator fun invoke(groupId: String): InviteResult {
        val old = groups.observeGroup(groupId).first()?.inviteCode ?: return InviteResult.Failed("Unknown group")
        groups.regenerateInviteCode(groupId)
        if (!sync.flush()) return InviteResult.Offline
        invites.withdraw(old)
        return share(groupId)
    }
}

class PreviewInvite(private val invites: InviteService) {
    /** Accepts a code or a whole link. */
    suspend operator fun invoke(text: String): PreviewResult {
        val code = InviteCodes.normalize(text)
        return if (InviteCodes.isValid(code)) invites.preview(code) else PreviewResult.NotFound
    }
}

sealed interface JoinResult {
    /** In the group (or already was); the group may still be arriving from the server. */
    data class Joined(val groupId: String) : JoinResult
    data object Offline : JoinResult

    /** The code changed or the group is full: ask for a new link. */
    data object Rejected : JoinResult
}

/** Joins with a previewed invite and waits for the group to reach this device. */
class JoinGroup(
    private val invites: InviteService,
    private val groups: GroupRepository,
    private val accounts: AccountService,
    private val arrival: Duration = 30.seconds,
) {
    suspend operator fun invoke(preview: InvitePreview): JoinResult {
        val uid = accounts.currentSession()?.uid ?: return JoinResult.Offline
        val local = groups.observeGroup(preview.groupId).first()
        if (local != null && uid in local.memberUids) return JoinResult.Joined(preview.groupId)
        return when (invites.join(preview)) {
            AccountResult.Done -> {
                // The sync engine pulls it as soon as "my groups" includes it.
                withTimeoutOrNull(arrival) {
                    groups.observeGroup(preview.groupId).first { it != null && uid in it.memberUids }
                }
                JoinResult.Joined(preview.groupId)
            }
            AccountResult.Offline -> JoinResult.Offline
            else -> JoinResult.Rejected
        }
    }
}

/** After joining: become a ghost member (same history and balance) or a new one. */
class ClaimMember(
    private val members: MemberRepository,
    private val profiles: ProfileRepository,
    private val accounts: AccountService,
) {
    /** [memberId] null adds a new member with my profile; nothing happens if I already have one. */
    suspend operator fun invoke(groupId: String, memberId: String?) {
        val uid = accounts.currentSession()?.uid
        if (members.observeMembers(groupId).first().mine(uid) != null) return
        val profile = profiles.profile.first()
        if (memberId != null) {
            members.claim(memberId)
        } else {
            members.addMe(groupId, profile?.name ?: DEFAULT_NAME, profile?.avatar)
        }
        profile?.payMethods?.orNull()?.let { members.publishMyPayMethods(it) }
    }

    private companion object {
        const val DEFAULT_NAME = "Yo"
    }
}
