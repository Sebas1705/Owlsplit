package es.sebas1705.owlsplit.data.models

/**
 * The latest published release: [version] as people read it (`1.0.0-beta.5`), [versionCode] as
 * Android compares builds, and [url] of its APK.
 */
data class LatestRelease(val version: String, val versionCode: Long, val url: String)
