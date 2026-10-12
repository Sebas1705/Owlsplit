package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Member

/** How someone came back to a member that had an account before. */
enum class Rejoin { SAME_ACCOUNT, OTHER_ACCOUNT }

/**
 * Which accounts each member had (docs/designs/2026-10-12-identidad-miembros.md). A member keeps
 * `pastUids` since P14; groups older than that only have their history, which records every join
 * (JOINED by the account) and every link change (`userUid` before and after).
 */
object MemberAccounts {
    private const val USER_UID = "userUid"

    /** Member id → accounts that had it and are no longer its own. */
    fun former(members: List<Member>, entries: List<ChangeLogEntry>): Map<String, Set<String>> {
        val seen = HashMap<String, MutableSet<String>>()
        members.forEach { seen.getOrPut(it.id) { LinkedHashSet() } += it.pastUids }
        memberEntries(entries).forEach { entry ->
            linkedUids(entry).forEach { seen.getOrPut(entry.entityId) { LinkedHashSet() } += it }
        }
        return members.associate { member -> member.id to (seen[member.id].orEmpty() - setOfNotNull(member.userUid)) }
    }

    /** The member I had and left, waiting for me: nobody else has claimed it since. */
    fun reclaimable(uid: String, members: List<Member>, entries: List<ChangeLogEntry>): Member? {
        val former = former(members, entries)
        return members.firstOrNull { it.userUid == null && !it.meta.deleted && uid in former[it.id].orEmpty() }
    }

    /** History entry id → how that join happened, for the joins of members that had an account before. */
    fun rejoins(entries: List<ChangeLogEntry>): Map<String, Rejoin> = buildMap {
        val seen = HashMap<String, MutableSet<String>>()
        memberEntries(entries).forEach { entry ->
            val had = seen.getOrPut(entry.entityId) { LinkedHashSet() }
            val gained = gainedUid(entry)
            if (gained != null && had.isNotEmpty()) {
                put(entry.id, if (gained in had) Rejoin.SAME_ACCOUNT else Rejoin.OTHER_ACCOUNT)
            }
            had += linkedUids(entry)
        }
    }

    private fun memberEntries(entries: List<ChangeLogEntry>) =
        entries.filter { it.entityType == EntityType.MEMBER }.sortedBy { it.createdAt }

    /** The account a member got in this entry, if it got one. */
    private fun gainedUid(entry: ChangeLogEntry): String? {
        val link = entry.diff.firstOrNull { it.field == USER_UID }
        return when {
            link != null -> link.after
            entry.action == ChangeAction.JOINED -> entry.authorUid
            else -> null
        }
    }

    private fun linkedUids(entry: ChangeLogEntry): List<String> = buildList {
        if (entry.action == ChangeAction.JOINED) add(entry.authorUid)
        entry.diff.firstOrNull { it.field == USER_UID }?.let { change ->
            change.before?.let(::add)
            change.after?.let(::add)
        }
    }
}
