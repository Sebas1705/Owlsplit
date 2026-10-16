package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.data.models.PreviewResult
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.JoinResult
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import kotlinx.coroutines.flow.first

enum class RejoinResult {
    /** In again, with the member I had (its expenses and balance). */
    JOINED,

    /** The code this device kept no longer opens the group (regenerated, or the group is full). */
    NEEDS_INVITE,
    OFFLINE,
}

/**
 * Going back into a group I left by mistake, without asking anyone for a link: a group I left stays on
 * this device (its history), invite code included, so it joins with that code and takes back my old
 * member, which kept its expenses as a member without account.
 */
class RejoinGroup(
    private val groups: GroupRepository,
    private val preview: PreviewInvite,
    private val join: JoinGroup,
    private val claim: ClaimMember,
) {
    suspend operator fun invoke(groupId: String): RejoinResult {
        val group = groups.observeGroup(groupId).first() ?: return RejoinResult.NEEDS_INVITE
        val found = when (val result = preview(group.inviteCode)) {
            is PreviewResult.Found -> result.preview
            PreviewResult.NotFound -> return RejoinResult.NEEDS_INVITE
            PreviewResult.Offline -> return RejoinResult.OFFLINE
        }
        // A code is only ever reused for the same group, but a stale copy must not open another one.
        if (found.groupId != groupId) return RejoinResult.NEEDS_INVITE
        return when (join(found)) {
            is JoinResult.Joined -> {
                if (!claim.reclaim(groupId)) claim(groupId, memberId = null)
                RejoinResult.JOINED
            }
            JoinResult.Offline -> RejoinResult.OFFLINE
            JoinResult.Rejected -> RejoinResult.NEEDS_INVITE
        }
    }
}
