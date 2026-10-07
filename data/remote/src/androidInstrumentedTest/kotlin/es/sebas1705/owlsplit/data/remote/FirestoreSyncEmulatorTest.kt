package es.sebas1705.owlsplit.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import dev.gitlive.firebase.initialize
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.repository.ExpenseInput
import es.sebas1705.owlsplit.data.repository.ExpenseRepositoryImpl
import es.sebas1705.owlsplit.data.repository.GroupInput
import es.sebas1705.owlsplit.data.repository.GroupRepositoryImpl
import es.sebas1705.owlsplit.data.repository.MemberRepositoryImpl
import es.sebas1705.owlsplit.data.repository.RepositoryContext
import es.sebas1705.owlsplit.data.repository.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Two devices (two Firebase apps, two anonymous users, two local databases) synchronising through
 * the Firestore and Auth emulators with the real rules. Run with scripts/run-sync-emulator-test.sh.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreSyncEmulatorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private inner class Device(name: String) {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val app = Firebase.initialize(
            context,
            FirebaseOptions(applicationId = "1:1:android:1", apiKey = "fake-key", projectId = PROJECT),
            name = "$name-${Random.nextInt(1_000_000)}",
        )
        val auth = Firebase.auth(app).apply { useEmulator(HOST, AUTH_PORT) }
        val firestore = Firebase.firestore(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            settings = firestoreSettings { cacheSettings = memoryCacheSettings { } }
        }
        val db = AppDatabase(AndroidSqliteDriver(AppDatabase.Schema, context, null))
        private val ctx = RepositoryContext(uid = { requireNotNull(auth.currentUser).uid })
        val groups = GroupRepositoryImpl(db, ctx, Dispatchers.IO)
        val members = MemberRepositoryImpl(db, ctx, Dispatchers.IO)
        val expenses = ExpenseRepositoryImpl(db, ctx, Dispatchers.IO)
        val engine = SyncEngine(db, FirestoreRemoteStore(firestore, auth), ctx, Dispatchers.IO)

        suspend fun start(): String {
            val uid = assertNotNull(auth.signInAnonymously().user).uid
            engine.start(scope, uid)
            return uid
        }

        suspend fun idle() = eventually { engine.pendingCount.first() == 0L }

        fun conflicts() = db.conflictQueries.countAll().executeAsOne()
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun twoDevicesConvergeAndAConcurrentEditBecomesOneConflict() = runBlocking {
        val ana = Device("ana")
        val luis = Device("luis")
        ana.start()
        val luisUid = luis.start()

        // Ana creates a trip with a dinner, offline-first, and the engine uploads it.
        val g = ana.groups.create(GroupInput("Ibiza 2026", "plane", "BLUE", "EUR"), "Ana")
        val anaMember = ana.members.observeMembers(g).first().single().id
        val e = ana.expenses.create(g, dinner(anaMember, "Cena"))
        ana.idle()

        // Luis joins with the invite code, as the rules expect from someone who cannot read the group yet.
        val code = assertNotNull(ana.groups.observeGroup(g).first()).inviteCode
        luis.firestore.collection("groups").document(g).update(
            "memberUids" to FieldValue.arrayUnion(luisUid),
            "lastJoinCode" to code,
            "version" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp,
            "updatedBy" to luisUid,
        )

        // The group, its member and the dinner reach Luis.
        eventually { luis.expenses.get(e)?.title == "Cena" }

        // Luis edits, Ana sees it.
        luis.expenses.update(e, dinner(anaMember, "Cena con postre"))
        eventually { ana.expenses.get(e)?.title == "Cena con postre" }
        luis.idle()

        // Both edit the same expense at once: one write wins, the other is kept as a conflict.
        ana.expenses.update(e, dinner(anaMember, "Ana's"))
        luis.expenses.update(e, dinner(anaMember, "Luis's"))
        ana.idle()
        luis.idle()
        eventually { ana.expenses.get(e)?.title == luis.expenses.get(e)?.title }
        eventually { ana.conflicts() + luis.conflicts() == 1L }
        assertEquals(1L, ana.conflicts() + luis.conflicts())
        // Echoes of our own uploads never count as conflicts: only the concurrent edit did.
    }

    private fun dinner(payer: String, title: String) = ExpenseInput(
        title, ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", 4_860, 1_000_000_000,
        mapOf(payer to 4_860L), SplitType.EQUAL, mapOf(payer to 1L),
    )

    private suspend fun eventually(condition: suspend () -> Boolean) = withTimeout(TIMEOUT_MILLIS) {
        while (!condition()) delay(POLL_MILLIS)
    }

    private companion object {
        const val PROJECT = "demo-owlsplit"
        const val HOST = "10.0.2.2"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 200L
    }
}
