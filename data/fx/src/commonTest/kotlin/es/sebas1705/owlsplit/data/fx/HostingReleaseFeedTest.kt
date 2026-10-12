package es.sebas1705.owlsplit.data.fx

import es.sebas1705.owlsplit.data.models.LatestRelease
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostingReleaseFeedTest {
    private fun feed(handler: MockRequestHandler) =
        HostingReleaseFeed(HttpClient(MockEngine(handler)), "https://owlsplit.web.app/download/latest.json")

    @Test
    fun readsTheLatestRelease() = runTest {
        val body = """{"version":"1.0.0-beta.5","tag":"v1.0.0-beta.5","versionCode":190,
            "url":"https://github.com/Sebas1705/Owlsplit/releases/download/v1.0.0-beta.5/Owlsplit-v1.0.0-beta.5.apk",
            "size":123,"sha256":"ab","date":"2026-10-12T10:00:00Z"}"""
        assertEquals(
            LatestRelease(
                "1.0.0-beta.5",
                190,
                "https://github.com/Sebas1705/Owlsplit/releases/download/v1.0.0-beta.5/Owlsplit-v1.0.0-beta.5.apk",
            ),
            feed { respond(body) }.latest(),
        )
    }

    @Test
    fun nothingWhenTheFileIsOlderMissingOrTheNetworkFails() = runTest {
        assertNull(feed { respond("""{"version":"1.0.0-beta.4","url":"https://x/a.apk"}""") }.latest())
        assertNull(feed { respondError(HttpStatusCode.NotFound) }.latest())
        assertNull(feed { respond("<html>") }.latest())
        assertNull(feed { error("offline") }.latest())
    }
}
