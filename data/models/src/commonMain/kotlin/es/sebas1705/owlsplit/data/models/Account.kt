package es.sebas1705.owlsplit.data.models

/** The signed-in user. [providers] are linked sign-in methods ("google.com"); empty while anonymous. */
data class Session(val uid: String, val isAnonymous: Boolean, val providers: List<String>)

/**
 * The user's own profile (not a group member). [avatar] is `accent:<GroupAccent>` for an initials
 * avatar on that colour; [currency] is the preferred ISO code for new groups.
 */
data class UserProfile(val name: String, val avatar: String, val currency: String)
