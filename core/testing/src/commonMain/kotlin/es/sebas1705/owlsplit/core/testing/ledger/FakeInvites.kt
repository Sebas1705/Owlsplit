package es.sebas1705.owlsplit.core.testing.ledger

import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.InvitePreview
import es.sebas1705.owlsplit.data.models.PreviewResult
import es.sebas1705.owlsplit.data.repository.invite.InviteService
import kotlinx.coroutines.flow.update

/** The server side of invites: published codes, and a join that adds the ledger's uid to the group. */
class FakeInvites(private val ledger: InMemoryLedger) : InviteService {
    val published = mutableMapOf<String, String>()
    var joinResult: AccountResult = AccountResult.Done
    var online = true

    override suspend fun publish(group: Group, memberCount: Int): AccountResult {
        if (!online) return AccountResult.Offline
        published[group.inviteCode] = group.id
        return AccountResult.Done
    }

    override suspend fun withdraw(code: String): AccountResult {
        published -= code
        return AccountResult.Done
    }

    override suspend fun preview(code: String): PreviewResult {
        if (!online) return PreviewResult.Offline
        val groupId = published[code] ?: return PreviewResult.NotFound
        val group = ledger.groupList.value.first { it.id == groupId }
        val count = ledger.memberList.value.count { it.groupId == groupId && it.active }
        return PreviewResult.Found(InvitePreview(code, groupId, group.name, group.icon, count))
    }

    override suspend fun join(preview: InvitePreview): AccountResult {
        if (!online) return AccountResult.Offline
        if (joinResult == AccountResult.Done) {
            ledger.groupList.update { list ->
                list.map { if (it.id == preview.groupId) it.copy(memberUids = it.memberUids + ledger.uid) else it }
            }
        }
        return joinResult
    }
}
