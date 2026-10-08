package es.sebas1705.owlsplit.feature.home

/** Where Inicio can take the user. */
class HomeNavigation(
    val openGroup: (String) -> Unit,
    val createGroup: () -> Unit,
    val addExpense: (groupId: String) -> Unit,
    val settle: (groupId: String) -> Unit,
    val link: () -> Unit,
    val joinGroup: () -> Unit,
)
