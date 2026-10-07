package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.json

/** Decodes an outbox payload into the document the server stores as [version] at [updatedAt]. */
fun decodeRemoteDoc(type: OutboxType, payload: String, version: Long, updatedAt: Long, updatedBy: String): RemoteDoc {
    fun SyncMeta.server() = copy(
        version = version,
        serverVersion = version,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
        syncState = SyncState.SYNCED,
    )
    return when (type) {
        OutboxType.GROUP -> json.decodeFromString(Group.serializer(), payload)
            .let { RemoteDoc.GroupDoc(it.copy(meta = it.meta.server())) }
        OutboxType.MEMBER -> json.decodeFromString(Member.serializer(), payload)
            .let { RemoteDoc.MemberDoc(it.copy(meta = it.meta.server())) }
        OutboxType.EXPENSE -> json.decodeFromString(Expense.serializer(), payload)
            .let { RemoteDoc.ExpenseDoc(it.copy(meta = it.meta.server())) }
        OutboxType.TRANSFER -> json.decodeFromString(Transfer.serializer(), payload)
            .let { RemoteDoc.TransferDoc(it.copy(meta = it.meta.server())) }
        OutboxType.CHANGE_LOG -> RemoteDoc.ChangeLogDoc(json.decodeFromString(ChangeLogEntry.serializer(), payload))
    }
}

/** True when [payload] carries the same content as this remote document (sync metadata aside, `deleted` included). */
internal fun RemoteDoc.sameContentAs(payload: String): Boolean {
    val local = decodeRemoteDoc(type, payload, version, updatedAt, updatedBy = "")
    return neutral() == local.neutral()
}

private fun SyncMeta.neutral() = SyncMeta(0, 0, null, "", deleted, SyncState.SYNCED)

private fun RemoteDoc.neutral(): Any = when (this) {
    is RemoteDoc.GroupDoc -> group.copy(meta = group.meta.neutral())
    is RemoteDoc.MemberDoc -> member.copy(meta = member.meta.neutral())
    is RemoteDoc.ExpenseDoc -> expense.copy(meta = expense.meta.neutral())
    is RemoteDoc.TransferDoc -> transfer.copy(meta = transfer.meta.neutral())
    is RemoteDoc.ChangeLogDoc -> entry
}
