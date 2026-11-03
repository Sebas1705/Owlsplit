package es.sebas1705.owlsplit.data.models

/**
 * Merging two members that are the same person (docs/designs/2026-11-03-group-admin.md, section 4).
 * Pure: the repositories apply it to every expense and payment, and the fakes do the same.
 */
object MemberMerge {
    /** Who is kept and who is absorbed, or null when the two cannot be merged (same one, or two accounts). */
    fun plan(a: Member, b: Member): Pair<Member, Member>? = when {
        a.id == b.id || a.groupId != b.groupId -> null
        a.userUid != null && b.userUid != null -> null
        // The one with an account is kept: an account never loses its member.
        a.userUid != null -> a to b
        b.userUid != null -> b to a
        else -> b to a
    }

    /** [kept] with what [absorbed] had: the accounts that left, the Google link and the pay methods it lacked. */
    fun merged(kept: Member, absorbed: Member): Member = kept.copy(
        pastUids = (kept.pastUids + absorbed.pastUids).distinct(),
        linked = kept.linked || absorbed.linked,
        payMethods = kept.payMethods ?: absorbed.payMethods,
        active = kept.active || absorbed.active,
    )

    /** The expense with [absorbed] replaced by [kept] (their amounts and weights added up); null if it does not name it. */
    fun expense(expense: Expense, absorbed: String, kept: String): Expense? {
        if (absorbed !in expense.payers && absorbed !in expense.splitValues) return null
        val both = absorbed in expense.splitValues && kept in expense.splitValues
        return expense.copy(
            payers = fold(expense.payers, absorbed, kept),
            splitValues = fold(expense.splitValues, absorbed, kept),
            // Equal shares count each person once: two people in one share are two weights.
            splitType = if (both && expense.splitType == SplitType.EQUAL) SplitType.WEIGHTS else expense.splitType,
        )
    }

    /** The payment with [absorbed] replaced by [kept]; null if it does not name it. */
    fun transfer(transfer: Transfer, absorbed: String, kept: String): Transfer? {
        if (transfer.fromMemberId != absorbed && transfer.toMemberId != absorbed) return null
        return transfer.copy(
            fromMemberId = if (transfer.fromMemberId == absorbed) kept else transfer.fromMemberId,
            toMemberId = if (transfer.toMemberId == absorbed) kept else transfer.toMemberId,
        )
    }

    private fun fold(values: Map<String, Long>, absorbed: String, kept: String): Map<String, Long> {
        val mine = values[absorbed] ?: return values
        return values - absorbed + (kept to ((values[kept] ?: 0L) + mine))
    }
}
