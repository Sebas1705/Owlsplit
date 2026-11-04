package es.sebas1705.owlsplit.data.remote

import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import dev.gitlive.firebase.initialize
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.Permission
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.PayMethods
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.TransferInput
import es.sebas1705.owlsplit.data.repository.ExpenseRepositoryImpl
import es.sebas1705.owlsplit.data.repository.GroupRepositoryImpl
import es.sebas1705.owlsplit.data.repository.MemberRepositoryImpl
import es.sebas1705.owlsplit.data.repository.RepositoryContext
import es.sebas1705.owlsplit.data.repository.TransferRepositoryImpl
import es.sebas1705.owlsplit.data.repository.sync.PushResult
import es.sebas1705.owlsplit.data.repository.sync.RemoteDoc
import es.sebas1705.owlsplit.data.repository.sync.RemoteStore
import es.sebas1705.owlsplit.data.repository.sync.SyncEngine
import es.sebas1705.owlsplit.data.repository.sync.decodeRemoteDoc
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
        val transfers = TransferRepositoryImpl(db, ctx, Dispatchers.IO)
        val store = OldBuildStore(FirestoreRemoteStore(firestore, auth), firestore, auth)
        val engine = SyncEngine(db, store, ctx, Dispatchers.IO)

        suspend fun start(): String {
            val uid = assertNotNull(auth.signInAnonymously().user).uid
            engine.start(scope, uid)
            return uid
        }

        suspend fun idle() = eventually { engine.pendingCount.first() == 0L }

        fun conflicts() = db.conflictQueries.countAll().executeAsOne()

        fun groupDoc(group: String) = firestore.collection("groups").document(group)

        /** What the server holds right now, not what this device believes it holds. */
        suspend fun serverExpense(group: String, id: String) =
            groupDoc(group).collection("expenses").document(id).read().data(ExpenseDto.serializer())

        suspend fun serverMember(group: String, id: String) =
            groupDoc(group).collection("members").document(id).read().data(MemberDto.serializer())

        suspend fun serverTransfer(group: String, id: String) =
            groupDoc(group).collection("transfers").document(id).read().data(TransferDto.serializer())

        private suspend fun DocumentReference.read() = get(Source.SERVER)
    }

    /** Ana's trip with Luis in it: Ana's member, two members without an account, and the invite code. */
    private class Trip(val group: String, val code: String, val ana: String, val beto: String, val carla: String)

    private suspend fun trip(ana: Device, luis: Device, luisUid: String): Trip {
        val g = ana.groups.create(GroupInput("Ibiza 2026", "plane", "BLUE", "EUR"), "Ana")
        val anaMember = ana.members.observeMembers(g).first().single().id
        val beto = ana.members.add(g, MemberInput("Beto"))
        val carla = ana.members.add(g, MemberInput("Carla"))
        ana.idle()
        val code = assertNotNull(ana.groups.observeGroup(g).first()).inviteCode
        luis.join(g, code, luisUid)
        eventually { luis.members.observeMembers(g).first().size == 3 }
        return Trip(g, code, anaMember, beto, carla)
    }

    /** As the rules expect from someone who cannot read the group yet. */
    private suspend fun Device.join(group: String, code: String, uid: String) = groupDoc(group).update(
        "memberUids" to FieldValue.arrayUnion(uid),
        "lastJoinCode" to code,
        "version" to FieldValue.increment(1),
        "updatedAt" to FieldValue.serverTimestamp,
        "updatedBy" to uid,
    )

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
        luis.join(g, code, luisUid)

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

    /** Firestore deep-merges maps: only a full replace takes keys out of `payers` and `splitValues`. */
    @Test
    fun editingAnExpenseRemovesThePayersAndParticipantsItNoLongerHas() = runBlocking {
        val ana = Device("ana")
        val luis = Device("luis")
        val anaUid = ana.start()
        val luisUid = luis.start()
        val t = trip(ana, luis, luisUid)
        val e = ana.expenses.create(t.group, shared("Cena", mapOf(t.ana to AMOUNT), t.ana, t.beto, t.carla))
        ana.idle()
        eventually { luis.expenses.get(e) != null }
        val created = assertNotNull(luis.expenses.get(e)).createdAt

        // Luis did not create it: a full replace has to keep createdBy and createdAt to pass the rules.
        luis.expenses.update(e, shared("Cena", mapOf(t.beto to AMOUNT), t.ana, t.beto))
        luis.idle()

        val stored = luis.serverExpense(t.group, e)
        assertEquals(mapOf(t.beto to AMOUNT), stored.payers)
        assertEquals(setOf(t.ana, t.beto), stored.splitValues.keys)
        assertEquals(2, stored.version)
        assertEquals(anaUid, stored.createdBy)
        assertEquals(created, stored.createdAt)

        // The other device gets the same expense, so the sums match everywhere.
        eventually { ana.expenses.get(e)?.meta?.version == 2L }
        val synced = assertNotNull(ana.expenses.get(e))
        assertEquals(mapOf(t.beto to AMOUNT), synced.payers)
        assertEquals(setOf(t.ana, t.beto), synced.splitValues.keys)
        assertEquals(0L, ana.conflicts() + luis.conflicts())
    }

    /**
     * What a device on a build that merged left behind: its own copy is right, the server and everyone
     * else have the union of the old and new maps. Any later edit from that device repairs them.
     */
    @Test
    fun theNextEditRepairsAnExpenseAnOlderBuildCorrupted() = runBlocking {
        val ana = Device("ana")
        val luis = Device("luis")
        ana.start()
        val luisUid = luis.start()
        val t = trip(ana, luis, luisUid)
        val e = ana.expenses.create(t.group, shared("Cena", mapOf(t.ana to AMOUNT), t.ana, t.beto, t.carla))
        ana.idle()
        eventually { luis.expenses.get(e) != null }

        ana.store.oldBuild = true
        ana.expenses.update(e, shared("Cena", mapOf(t.beto to AMOUNT), t.ana, t.beto))
        ana.idle()
        ana.store.oldBuild = false

        // The real deep merge: both payers on the server, the sum broken, Carla still charged.
        val corrupted = ana.serverExpense(t.group, e)
        assertEquals(mapOf(t.ana to AMOUNT, t.beto to AMOUNT), corrupted.payers)
        assertEquals(setOf(t.ana, t.beto, t.carla), corrupted.splitValues.keys)
        eventually { luis.expenses.get(e)?.meta?.version == 2L }
        assertEquals(corrupted.payers, assertNotNull(luis.expenses.get(e)).payers)
        assertEquals(mapOf(t.beto to AMOUNT), assertNotNull(ana.expenses.get(e)).payers)

        // After updating, editing only the title uploads the whole (right) document.
        ana.expenses.update(e, shared("Cena con postre", mapOf(t.beto to AMOUNT), t.ana, t.beto))
        ana.idle()

        val repaired = ana.serverExpense(t.group, e)
        assertEquals(mapOf(t.beto to AMOUNT), repaired.payers)
        assertEquals(setOf(t.ana, t.beto), repaired.splitValues.keys)
        eventually { luis.expenses.get(e)?.meta?.version == 3L }
        val synced = assertNotNull(luis.expenses.get(e))
        assertEquals(mapOf(t.beto to AMOUNT), synced.payers)
        assertEquals(setOf(t.ana, t.beto), synced.splitValues.keys)
        assertEquals(0L, ana.conflicts() + luis.conflicts())
    }

    /** The other documents still pass the rules, and the group keeps the key the client does not know. */
    @Test
    fun groupMemberAndTransferEditsKeepWhatTheyMust() = runBlocking {
        val ana = Device("ana")
        val luis = Device("luis")
        val anaUid = ana.start()
        val luisUid = luis.start()
        val t = trip(ana, luis, luisUid)

        // Group (edited by its admin, Ana): a full replace would drop lastJoinCode, and the rules reject that.
        ana.groups.update(t.group, GroupInput("Ibiza 2027", "plane", "BLUE", "EUR"))
        ana.idle()
        val group = ana.groupDoc(t.group).get(Source.SERVER)
        assertEquals("Ibiza 2027", group.get<String>("name"))
        assertEquals(t.code, group.get<String>("lastJoinCode"))
        assertEquals(anaUid, group.get<String>("createdBy"))

        // Member: the nested pay methods lose the key that was removed.
        ana.members.publishMyPayMethods(PayMethods(bizum = "600111222", paypal = "ana"))
        ana.idle()
        ana.members.publishMyPayMethods(PayMethods(bizum = "600111222"))
        ana.members.update(t.beto, MemberInput("Berto", defaultWeight = 2))
        ana.idle()
        assertEquals(PayMethods(bizum = "600111222"), ana.serverMember(t.group, t.ana).payMethods)
        assertEquals("Berto", ana.serverMember(t.group, t.beto).name)

        // Transfer: Luis undoes a payment Ana recorded.
        val p = ana.transfers.create(
            t.group,
            TransferInput(t.beto, t.ana, AMOUNT, "EUR", RATE_ONE, LocalDate(2026, 7, 13)),
        )
        ana.idle()
        eventually { luis.transfers.observeTransfers(t.group).first().any { it.id == p } }
        luis.transfers.delete(p)
        luis.idle()
        val transfer = luis.serverTransfer(t.group, p)
        assertEquals(true, transfer.deleted)
        assertEquals(anaUid, transfer.createdBy)
        assertEquals(0L, ana.conflicts() + luis.conflicts())
    }

    @Test
    fun theAdminRoleThePermissionsAndAMergeReachTheOtherDevice() = runBlocking {
        val ana = Device("ana")
        val luis = Device("luis")
        ana.start()
        val luisUid = luis.start()
        val t = trip(ana, luis, luisUid)
        // Luis is Beto in this group.
        luis.members.claim(t.beto)
        luis.idle()
        val luisMember = t.beto
        eventually { ana.members.observeMembers(t.group).first().first { it.id == t.beto }.userUid == luisUid }
        val anaGroup = ana.groups.observeGroup(t.group).first()!!
        assertEquals(t.ana, anaGroup.adminMemberId)

        // The admin restricts Luis: the other device sees it.
        ana.members.setDeniedPermissions(luisMember, setOf(Permission.RECORD_PAYMENTS))
        ana.idle()
        eventually {
            luis.members.observeMembers(t.group).first().first { it.id == luisMember }.deniedPermissions ==
                setOf(Permission.RECORD_PAYMENTS)
        }

        // Handing the group over: Luis can now edit it, and merge people.
        ana.groups.setAdmin(t.group, luisMember)
        ana.idle()
        eventually { luis.groups.observeGroup(t.group).first()?.adminMemberId == luisMember }
        luis.members.setDeniedPermissions(luisMember, emptySet())
        luis.groups.update(t.group, GroupInput("Ibiza 2028", "plane", "BLUE", "EUR"))
        luis.idle()
        assertEquals("Ibiza 2028", luis.groupDoc(t.group).get(Source.SERVER).get<String>("name"))

        val e = ana.expenses.create(t.group, shared("Cena", mapOf(t.carla to AMOUNT), t.ana, t.beto, t.carla))
        // Payments of the member merged in, recorded by the new admin: one moves to Beto, one between the two disappears.
        val toBeto = luis.transfers.create(
            t.group,
            TransferInput(t.carla, t.ana, AMOUNT, "EUR", RATE_ONE, LocalDate(2026, 7, 13))
        )
        val between = luis.transfers.create(
            t.group,
            TransferInput(t.carla, t.beto, AMOUNT, "EUR", RATE_ONE, LocalDate(2026, 7, 13))
        )
        ana.idle()
        eventually { luis.expenses.get(e) != null }
        eventually { luis.transfers.observeTransfers(t.group).first().size == 2 }
        // Carla was somebody's abandoned profile: merging hands her old account to Beto (the server must allow it).
        luis.db.memberEntityQueries.upsert(
            luis.db.memberEntityQueries.selectById(t.carla).executeAsOne().copy(past_uids = "[\"old-uid\"]"),
        )
        assertEquals(t.beto, luis.members.merge(t.carla, t.beto))
        luis.idle()
        assertEquals(listOf("old-uid"), ana.serverMember(t.group, t.beto).pastUids)
        luis.idle()
        eventually { ana.expenses.get(e)?.payers?.keys == setOf(t.beto) }
        eventually { ana.members.observeMembers(t.group).first().none { it.id == t.carla } }
        assertEquals(t.beto, ana.serverTransfer(t.group, toBeto).fromMemberId)
        assertEquals(true, ana.serverTransfer(t.group, between).deleted)
        assertEquals(0L, ana.conflicts() + luis.conflicts())
    }

    private fun shared(title: String, payers: Map<String, Long>, vararg participants: String) = ExpenseInput(
        title, ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", AMOUNT, RATE_ONE,
        payers, SplitType.EQUAL, participants.associateWith { 1L },
    )

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
        const val AMOUNT = 9_000L
        const val RATE_ONE = 1_000_000_000L
    }
}

/**
 * The real store, except that while [oldBuild] is on expenses are uploaded as builds before the full
 * replace did, with `merge = true`: a device that has not updated yet, against the real emulator.
 */
private class OldBuildStore(
    private val real: RemoteStore,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : RemoteStore by real {
    var oldBuild = false

    override suspend fun push(
        type: OutboxType,
        groupId: String,
        entityId: String,
        version: Long,
        payload: String,
    ): PushResult {
        if (!oldBuild || type != OutboxType.EXPENSE) return real.push(type, groupId, entityId, version, payload)
        val uid = requireNotNull(auth.currentUser).uid
        val doc = decodeRemoteDoc(type, payload, version, updatedAt = 0, updatedBy = uid) as RemoteDoc.ExpenseDoc
        firestore.collection("groups").document(groupId).collection("expenses").document(entityId)
            .set(ExpenseDto.serializer(), doc.expense.toDto(Stamp(version, uid)), merge = true)
        return PushResult.Ok
    }
}
