package es.sebas1705.owlsplit.config

import android.os.Build
import es.sebas1705.owlsplit.BuildConfig
import es.sebas1705.owlsplit.core.security.appContext

actual val isDebugBuild: Boolean = BuildConfig.DEBUG

/** Installs from Google Play update through Play: the download page's notice is only for the APK. */
actual val updatableVersionCode: Long?
    get() = if (BuildConfig.DEBUG || installedFromPlay()) null else BuildConfig.VERSION_CODE.toLong()

private const val PLAY_STORE = "com.android.vending"

/** Installed by the Play Store: Play updates it, and can do so from inside the app (`PlayStoreUpdates`). */
internal fun installedFromPlay(): Boolean {
    val packages = appContext.packageManager
    val name = appContext.packageName
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        packages.getInstallSourceInfo(name).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        packages.getInstallerPackageName(name)
    }
    return installer == PLAY_STORE
}
