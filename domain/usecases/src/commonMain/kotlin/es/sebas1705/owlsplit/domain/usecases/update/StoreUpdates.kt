package es.sebas1705.owlsplit.domain.usecases.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * An update offered by the store the app was installed from (Google Play), which downloads it in the
 * background while the app keeps working. Stores do not tell the version name, only its code.
 */
sealed interface StoreUpdate {
    val versionCode: Long

    /** The store has a newer version; the user has not asked for it yet. */
    data class Available(override val versionCode: Long) : StoreUpdate

    /** The user accepted it and the store is downloading it. */
    data class Downloading(override val versionCode: Long) : StoreUpdate

    /** Downloaded: a restart installs it. */
    data class Downloaded(override val versionCode: Long) : StoreUpdate
}

/**
 * Updates through the app store (Google Play In-App Updates on Play installs). Installs that do not come
 * from a store with this API (iOS, APKs from the download page, debug builds) use [NoStoreUpdates].
 */
interface StoreUpdates {
    /** The store's update and how far it got; null when there is none. */
    val update: Flow<StoreUpdate?>

    /** Asks the store to download the available update (the store confirms it with the user first). */
    suspend fun start()

    /** Restarts the app to install the downloaded update. */
    suspend fun complete()
}

/** No store updates: the app is updated some other way (or by the store on its own). */
object NoStoreUpdates : StoreUpdates {
    override val update: Flow<StoreUpdate?> = flowOf(null)
    override suspend fun start() = Unit
    override suspend fun complete() = Unit
}
