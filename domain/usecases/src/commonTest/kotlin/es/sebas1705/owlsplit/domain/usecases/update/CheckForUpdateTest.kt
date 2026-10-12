package es.sebas1705.owlsplit.domain.usecases.update

import es.sebas1705.owlsplit.data.models.LatestRelease
import es.sebas1705.owlsplit.data.repository.update.ReleaseFeed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckForUpdateTest {
    private val beta5 = LatestRelease("1.0.0-beta.5", 190, "https://x/a.apk")
    private val feed = object : ReleaseFeed {
        var asked = 0
        override suspend fun latest(): LatestRelease? = beta5.also { asked++ }
    }

    @Test
    fun offersOnlyANewerRelease() = runTest {
        assertEquals(beta5, CheckForUpdate(feed, installedCode = 180)())
        assertNull(CheckForUpdate(feed, installedCode = 190)())
        // A build of main, ahead of the last tag.
        assertNull(CheckForUpdate(feed, installedCode = 201)())
    }

    @Test
    fun buildsThatCannotUpdateThisWayNeverAsk() = runTest {
        assertNull(CheckForUpdate(feed, installedCode = null)())
        assertEquals(0, feed.asked)
    }
}
