package es.sebas1705.owlsplit.data.models

/** The signed-in user. [providers] are linked sign-in methods ("google.com"); empty while anonymous. */
data class Session(val uid: String, val isAnonymous: Boolean, val providers: List<String>)

/**
 * The user's own profile (not a group member). [avatar] is `accent:<GroupAccent>` for an initials
 * avatar on that colour; [currency] is the preferred ISO code for new groups.
 */
data class UserProfile(val name: String, val avatar: String, val currency: String)

/** Outcome of an account operation, as the UI needs to tell them apart. */
sealed interface AccountResult {
    data object Done : AccountResult

    /** The credential already belongs to another account (linking). */
    data object CredentialInUse : AccountResult

    /** The provider asks for a fresh sign-in before a sensitive operation (deleting the account). */
    data object RecentLoginRequired : AccountResult

    /** No connection, or the server could not be reached. */
    data object Offline : AccountResult

    data class Failed(val reason: String) : AccountResult
}

/** Asking the platform for a Google ID token. */
sealed interface GoogleTokenResult {
    /** [accessToken]: required by Firebase on iOS (Google Sign-In SDK); Android works with the ID token only. */
    data class Token(val idToken: String, val accessToken: String? = null) : GoogleTokenResult
    data object Cancelled : GoogleTokenResult
    data class Failed(val reason: String) : GoogleTokenResult
}
