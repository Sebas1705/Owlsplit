package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.Member
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** In-memory database with a fixed clock, predictable ids and a settable user. */
class TestEnv(val scope: TestScope = TestScope()) {
    var uid = "uid-ana"
    var nowMillis = 1_000L
    private var nextId = 0
    private var nextCode = 0

    val db: AppDatabase = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).let {
        AppDatabase.Schema.create(it)
        AppDatabase(it)
    }

    val ctx = RepositoryContext(
        uid = { uid },
        clock = object : Clock {
            // Tied to the test scheduler so that delay() also moves the engine clock.
            override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis + scope.testScheduler.currentTime)
        },
        ids = { "id${nextId++}" },
        inviteCodes = { "CODE${nextCode++}" },
    )

    val dispatcher = StandardTestDispatcher(scope.testScheduler)
    val groups = GroupRepositoryImpl(db, ctx, dispatcher)
    val members = MemberRepositoryImpl(db, ctx, dispatcher)
    val changeLog = ChangeLogRepositoryImpl(db, dispatcher)
    val expenses = ExpenseRepositoryImpl(db, ctx, dispatcher)
    val transfers = TransferRepositoryImpl(db, ctx, dispatcher)

    fun outbox() = db.outboxQueries.selectAll().executeAsList()
}

suspend fun MemberRepository.observeMembersOnce(groupId: String): List<Member> = observeMembers(groupId).first()
