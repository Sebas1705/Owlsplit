package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlin.math.min

/** How a pull ended: [DONE], [RETRY] later (network), or [STOP] (no access: "my groups" handles it). */
internal enum class PullStep { DONE, RETRY, STOP }

/**
 * Downloads one group (docs/SYNC.md, "Download"). The only live listener is the group's changelog:
 * every change to a member, expense or transfer that people see writes a history entry after the
 * document itself, so a new entry says which document to fetch. Members, expenses and transfers are
 * read by query only once per group ([bootstrap]) and when an inactive group is opened ([catchUp]).
 */
internal class GroupPuller(
    private val local: SyncLocalStore,
    private val marks: PullMarks,
    private val remote: RemoteStore,
    private val receive: (RemoteDoc) -> Unit,
) {

    /** Keeps the group up to date until cancelled, or until access to it is lost. */
    suspend fun listen(groupId: String) {
        var attempt = 0
        while (true) {
            val step = bootstrap(groupId).orElse {
                val since = since(groupId, RemoteCollection.CHANGELOG)
                remote.observe(groupId, RemoteCollection.CHANGELOG, since)
                    .map { docs -> applyChangelog(groupId, docs).also { if (it == PullStep.DONE) attempt = 0 } }
                    .firstOrNull { it != PullStep.DONE }
                    // The listener ended on its own: the group denied us (my groups notices it).
                    ?: PullStep.STOP
            }
            if (step != PullStep.RETRY) return
            delay(min(RETRY_BASE_MILLIS shl min(attempt++, MAX_SHIFT), RETRY_MAX_MILLIS))
        }
    }

    /**
     * One-off pull of a group without a live listener (archived), and of the members' personal
     * fields (pay methods, linked account), which stay out of the history and so out of the changelog.
     */
    suspend fun catchUp(groupId: String, listened: Boolean): PullStep = when {
        // Its listener is about to bootstrap it.
        !marks.isBootstrapped(groupId) && listened -> PullStep.DONE
        !marks.isBootstrapped(groupId) -> bootstrap(groupId)
        listened -> pullCollection(groupId, RemoteCollection.MEMBERS)
        else -> pullChangelog(groupId).orElse { pullCollection(groupId, RemoteCollection.MEMBERS) }
    }

    /** One-off read of the changelog from its cursor, fetching what the new entries name. */
    private suspend fun pullChangelog(groupId: String): PullStep {
        val since = since(groupId, RemoteCollection.CHANGELOG)
        return when (val pulled = remote.pull(groupId, RemoteCollection.CHANGELOG, since)) {
            is PullResult.Docs -> applyChangelog(groupId, pulled.docs)
            PullResult.NoAccess -> PullStep.STOP
            PullResult.Transient -> PullStep.RETRY
        }
    }

    /**
     * First pull of a group by query, from the cursors (zero for a new group: everything). The
     * changelog goes first, so the entries it stores already have their documents pulled right after
     * (a document is always written before its entry) and the listener does not fetch them again.
     * Installs that synced with one listener per collection also go through here once.
     */
    private suspend fun bootstrap(groupId: String): PullStep {
        if (marks.isBootstrapped(groupId)) return PullStep.DONE
        for (collection in BOOTSTRAP_ORDER) {
            val step = pullCollection(groupId, collection)
            if (step != PullStep.DONE) return step
        }
        marks.setBootstrapped(groupId)
        return PullStep.DONE
    }

    private suspend fun pullCollection(groupId: String, collection: RemoteCollection): PullStep =
        when (val pulled = remote.pull(groupId, collection, since(groupId, collection))) {
            is PullResult.Docs -> {
                pulled.docs.forEach(receive)
                pulled.docs.maxOfOrNull { it.updatedAt }?.let { local.advanceCursor(groupId, collection, it) }
                PullStep.DONE
            }
            PullResult.NoAccess -> PullStep.STOP
            PullResult.Transient -> PullStep.RETRY
        }

    /**
     * Fetches the documents that new history entries point at, and only then stores the entries and
     * advances the cursor: if anything fails, the same entries come again and nothing is skipped.
     * Entries already here (written on this device, or handled before) fetch nothing.
     */
    private suspend fun applyChangelog(groupId: String, docs: List<RemoteDoc>): PullStep {
        val targets = docs.filterIsInstance<RemoteDoc.ChangeLogDoc>()
            .filterNot { marks.isChangeLogKnown(it.id) }
            .mapNotNull { it.entry.entityType.documentType()?.to(it.entry.entityId) }
            .distinct()
        for (chunk in targets.chunked(FETCH_PARALLELISM)) {
            val fetched =
                coroutineScope { chunk.map { (type, id) -> async { remote.fetch(type, groupId, id) } }.awaitAll() }
            for (result in fetched) {
                when (result) {
                    is FetchResult.Found -> receive(result.doc)
                    // Never written (rejected) or unreadable by this version: nothing to apply.
                    FetchResult.Missing, FetchResult.Unreadable -> Unit
                    FetchResult.NoAccess -> return PullStep.STOP
                    FetchResult.Transient -> return PullStep.RETRY
                }
            }
        }
        docs.forEach(receive)
        docs.maxOfOrNull { it.updatedAt }?.let { local.advanceCursor(groupId, RemoteCollection.CHANGELOG, it) }
        return PullStep.DONE
    }

    /** Re-read a little before the cursor: concurrent writes can carry close server times. */
    private fun since(groupId: String, collection: RemoteCollection): Long =
        (local.cursor(groupId, collection) - CURSOR_MARGIN_MILLIS).coerceAtLeast(0)

    private inline fun PullStep.orElse(next: () -> PullStep): PullStep = if (this == PullStep.DONE) next() else this

    private companion object {
        const val CURSOR_MARGIN_MILLIS = 5 * 60 * 1_000L
        const val FETCH_PARALLELISM = 10
        const val RETRY_BASE_MILLIS = 1_000L
        const val RETRY_MAX_MILLIS = 5 * 60 * 1_000L
        const val MAX_SHIFT = 20
        val BOOTSTRAP_ORDER = listOf(
            RemoteCollection.CHANGELOG,
            RemoteCollection.MEMBERS,
            RemoteCollection.EXPENSES,
            RemoteCollection.TRANSFERS,
        )
    }
}

/** The document an entry is about; the group document itself comes with "my groups". */
private fun EntityType.documentType(): OutboxType? = when (this) {
    EntityType.GROUP -> null
    EntityType.MEMBER -> OutboxType.MEMBER
    EntityType.EXPENSE -> OutboxType.EXPENSE
    EntityType.TRANSFER -> OutboxType.TRANSFER
}
