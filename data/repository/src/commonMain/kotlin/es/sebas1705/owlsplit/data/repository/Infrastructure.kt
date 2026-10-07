package es.sebas1705.owlsplit.data.repository

import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Uid of the signed-in user; P8 backs it with Firebase Auth. */
fun interface UidProvider {
    fun currentUid(): String
}

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { Uuid.random().toString() }
    }
}

/** Short, unambiguous invite codes (no 0/O/1/I). */
fun interface InviteCodeGenerator {
    fun newCode(): String

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val LENGTH = 8
        val Random = InviteCodeGenerator {
            (1..LENGTH).map { ALPHABET[kotlin.random.Random.nextInt(ALPHABET.length)] }.joinToString("")
        }
    }
}

/** Everything a repository needs besides the database. */
data class RepositoryContext(
    val uid: UidProvider,
    val clock: Clock = Clock.System,
    val ids: IdGenerator = IdGenerator.Random,
    val inviteCodes: InviteCodeGenerator = InviteCodeGenerator.Random,
)
