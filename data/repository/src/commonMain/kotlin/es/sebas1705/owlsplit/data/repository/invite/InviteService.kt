package es.sebas1705.owlsplit.data.repository.invite

import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.InvitePreview
import es.sebas1705.owlsplit.data.models.PreviewResult

/** Invitations on the server. Implemented in `data:remote`. */
interface InviteService {
    /** Creates or refreshes `invites/{group.inviteCode}`; the group must already be on the server. */
    suspend fun publish(group: Group, memberCount: Int): AccountResult

    /** Removes an old code after regenerating it. */
    suspend fun withdraw(code: String): AccountResult

    suspend fun preview(code: String): PreviewResult

    /**
     * Adds the signed-in user to the group with the code, the only write the rules allow a
     * non-member (arrayUnion of their uid, version + 1, server time).
     */
    suspend fun join(preview: InvitePreview): AccountResult
}
