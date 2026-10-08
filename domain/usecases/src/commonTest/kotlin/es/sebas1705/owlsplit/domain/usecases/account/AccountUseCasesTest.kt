package es.sebas1705.owlsplit.domain.usecases.account

import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.account.ProfileRemote
import es.sebas1705.owlsplit.data.repository.sync.SyncControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountUseCasesTest {
    private val log = mutableListOf<String>()

    private fun group(id: String, uids: List<String>) = Group(
        id, id, "plane", "BLUE", "EUR", null, null, "CODE", uids, emptyList(), "uid-ana", 0,
        SyncMeta(1, 1, 1, "uid-ana", false, SyncState.SYNCED),
    )

    private inner class Groups(list: List<Group>) : GroupRepository {
        private val all = MutableStateFlow(list)
        override fun observeGroups(): Flow<List<Group>> = all
        override fun observeGroup(id: String): Flow<Group?> = error("unused")
        override suspend fun create(input: GroupInput, creatorName: String): String = error("unused")
        override suspend fun update(id: String, input: GroupInput) = error("unused")
        override suspend fun setArchived(id: String, archived: Boolean) = error("unused")
        override suspend fun leave(id: String) {
            log += "leave $id"
        }
    }

    private inner class Sync(var online: Boolean = true, val lost: Set<String> = emptySet()) : SyncControl {
        override suspend fun flush(): Boolean = online.also { log += "flush" }
        override fun isAccessLost(groupId: String) = groupId in lost
    }

    private inner class Accounts(private val current: Session?, private val deleteResult: AccountResult = AccountResult.Done) :
        AccountService {
        override val session: Flow<Session?> = MutableStateFlow(current)
        override fun currentSession() = current
        override suspend fun signInAnonymously() = AccountResult.Done
        override suspend fun linkGoogle(idToken: String) = AccountResult.Done
        override suspend fun signInWithGoogle(idToken: String) = AccountResult.Done
        override suspend fun reauthenticateGoogle(idToken: String) = AccountResult.Done
        override suspend fun signOut() {
            log += "signOut"
        }
        override suspend fun deleteUser() = deleteResult.also { log += "deleteUser" }
    }

    private inner class Profiles : ProfileRemote {
        override suspend fun save(session: Session, profile: UserProfile) = AccountResult.Done
        override suspend fun delete(uid: String) = AccountResult.Done.also { log += "deleteProfile $uid" }
        override suspend fun load(uid: String): UserProfile? = null
    }

    private val reset = DeviceReset { log += "reset" }
    private val ana = Session("uid-ana", isAnonymous = true, providers = emptyList())

    @Test
    fun deletingLeavesMyGroupsThenRemovesProfileUserAndLocalData() = runTest {
        val groups = Groups(
            listOf(
                group("g1", listOf("uid-ana", "uid-luis")),
                group("lost", listOf("uid-ana")),
                group("old", listOf("uid-luis"))
            ),
        )
        val result = DeleteAccount(groups, Sync(lost = setOf("lost")), Accounts(ana), Profiles(), reset)()
        assertEquals(AccountResult.Done, result)
        assertEquals(listOf("flush", "leave g1", "flush", "deleteProfile uid-ana", "deleteUser", "reset"), log)
    }

    @Test
    fun deletingOfflineChangesNothing() = runTest {
        val result =
            DeleteAccount(
                Groups(listOf(group("g1", listOf("uid-ana")))),
                Sync(online = false),
                Accounts(ana),
                Profiles(),
                reset
            )()
        assertEquals(AccountResult.Offline, result)
        assertEquals(listOf("flush"), log)
    }

    @Test
    fun aRecentLoginRequestKeepsLocalDataSoItCanBeRetried() = runTest {
        val accounts = Accounts(ana.copy(isAnonymous = false), deleteResult = AccountResult.RecentLoginRequired)
        val result = DeleteAccount(Groups(emptyList()), Sync(), accounts, Profiles(), reset)()
        assertEquals(AccountResult.RecentLoginRequired, result)
        assertFalse("reset" in log)
    }

    @Test
    fun onlyLinkedAccountsSignOutAndOnlyOnceEverythingIsUploaded() = runTest {
        assertTrue(SignOut(Sync(), Accounts(ana), reset)() is AccountResult.Failed)
        val linked = ana.copy(isAnonymous = false, providers = listOf("google.com"))
        assertEquals(AccountResult.Offline, SignOut(Sync(online = false), Accounts(linked), reset)())
        log.clear()
        assertEquals(AccountResult.Done, SignOut(Sync(), Accounts(linked), reset)())
        assertEquals(listOf("flush", "signOut", "reset"), log)
    }

    @Test
    fun linkingIsSuggestedToAnonymousUsersWithAGroupUntilDismissed() {
        val linked = ana.copy(isAnonymous = false)
        assertTrue(ShouldSuggestLinking(ana, groupCount = 1, dismissed = false))
        assertFalse(ShouldSuggestLinking(ana, groupCount = 0, dismissed = false))
        assertFalse(ShouldSuggestLinking(ana, groupCount = 3, dismissed = true))
        assertFalse(ShouldSuggestLinking(linked, groupCount = 3, dismissed = false))
        assertFalse(ShouldSuggestLinking(null, groupCount = 3, dismissed = false))
    }
}
