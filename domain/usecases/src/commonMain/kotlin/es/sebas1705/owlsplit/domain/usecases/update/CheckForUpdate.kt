package es.sebas1705.owlsplit.domain.usecases.update

import es.sebas1705.owlsplit.data.models.LatestRelease
import es.sebas1705.owlsplit.data.repository.update.ReleaseFeed

/**
 * A newer release than the one installed, if there is one (docs/plans/2026-10-12-p15-updates.md).
 * [installedCode] is null where the app cannot update itself this way (debug builds, iOS): then it
 * never asks. Builds of main have a higher code than the last tag, so testers are not told to go back.
 */
class CheckForUpdate(private val feed: ReleaseFeed, private val installedCode: Long?) {
    suspend operator fun invoke(): LatestRelease? {
        val installed = installedCode ?: return null
        return feed.latest()?.takeIf { it.versionCode > installed }
    }
}
