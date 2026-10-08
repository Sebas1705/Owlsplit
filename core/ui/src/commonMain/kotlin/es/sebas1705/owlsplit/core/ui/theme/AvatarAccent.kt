package es.sebas1705.owlsplit.core.ui.theme

private const val AVATAR_PREFIX = "accent:"

/** Encodes an initials avatar on this colour, as stored in `UserProfile.avatar`. */
fun GroupAccent.toAvatar(): String = AVATAR_PREFIX + name

/** The colour of an initials avatar; BLUE for anything else (empty, or a future photo URL). */
fun avatarAccent(avatar: String?): GroupAccent =
    avatar?.removePrefix(AVATAR_PREFIX)?.let { name -> GroupAccent.entries.firstOrNull { it.name == name } }
        ?: GroupAccent.BLUE
