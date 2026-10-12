package es.sebas1705.owlsplit.data.fx

import es.sebas1705.owlsplit.data.models.LatestRelease
import es.sebas1705.owlsplit.data.repository.update.ReleaseFeed
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The download page's `/download/latest.json`, written by deploy-firebase.yml after every release
 * (docs/plans/2026-10-12-p15-updates.md): `{version, versionCode, url, …}`.
 */
class HostingReleaseFeed(private val client: HttpClient, private val url: String) : ReleaseFeed {

    // Any transport or parsing failure means "nothing to offer": the app never nags offline.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun latest(): LatestRelease? = try {
        val response = client.get(url)
        if (response.status.isSuccess()) parse(response.bodyAsText()) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun parse(body: String): LatestRelease? {
        val json = Json.parseToJsonElement(body).jsonObject
        val version = json.text("version") ?: return null
        val code = json["versionCode"]?.jsonPrimitive?.longOrNull ?: return null
        val apk = json.text("url") ?: return null
        return LatestRelease(version, code, apk)
    }

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
