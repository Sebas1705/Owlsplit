package es.sebas1705.owlsplit.share

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.core.ui.components.BankApp
import es.sebas1705.owlsplit.core.ui.components.BankApps

/**
 * The banking apps installed, by their launcher intent (the manifest's <queries> makes them visible on
 * Android 11+), and the one chosen for Bizum, kept on this device.
 */
class AndroidBankApps(private val context: Context, private val activities: ResumedActivity) : BankApps {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun launchIntent(id: String): Intent? = context.packageManager.getLaunchIntentForPackage(id)

    override fun installed(): List<BankApp> = BankApps.installedAmong { launchIntent(it) != null }

    override fun open(app: BankApp): Boolean {
        val intent = launchIntent(app.id) ?: return false
        // From the activity in front; without one, as a new task.
        activities.current?.startActivity(intent) ?: context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    override fun preferred(): BankApp? = BankApps.preferredAmong(installed(), prefs.getString(KEY, null))

    override fun remember(app: BankApp) = prefs.edit { putString(KEY, app.id) }

    private companion object {
        const val PREFS = "owlsplit_bank"
        const val KEY = "bizum_bank"
    }
}
