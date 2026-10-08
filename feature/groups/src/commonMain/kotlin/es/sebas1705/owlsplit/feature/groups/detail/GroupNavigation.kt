package es.sebas1705.owlsplit.feature.groups.detail

/** Where the group screen can take the user. */
@Suppress("LongParameterList") // One callback per destination: splitting it would only add indirection.
class GroupNavigation(
    val back: () -> Unit,
    val members: () -> Unit,
    val edit: () -> Unit,
    val addExpense: () -> Unit,
    val openExpense: (String) -> Unit,
    val recordPayment: () -> Unit,
    val history: () -> Unit,
    val invite: () -> Unit,
)
