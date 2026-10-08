package es.sebas1705.owlsplit.data.models

/** What someone sees before joining: `invites/{code}` (docs/FIREBASE.md). */
data class InvitePreview(
    val code: String,
    val groupId: String,
    val groupName: String,
    val groupIcon: String,
    val memberCount: Int,
)

sealed interface PreviewResult {
    data class Found(val preview: InvitePreview) : PreviewResult
    data object NotFound : PreviewResult
    data object Offline : PreviewResult
}
