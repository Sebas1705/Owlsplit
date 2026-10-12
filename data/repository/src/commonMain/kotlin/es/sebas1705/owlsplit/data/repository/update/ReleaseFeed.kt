package es.sebas1705.owlsplit.data.repository.update

import es.sebas1705.owlsplit.data.models.LatestRelease

/** Where the app learns about new releases (the download page's `latest.json`, in `data:fx`). */
interface ReleaseFeed {
    /** Null when it cannot be read (offline, no release yet, an older file without a code). */
    suspend fun latest(): LatestRelease?
}
