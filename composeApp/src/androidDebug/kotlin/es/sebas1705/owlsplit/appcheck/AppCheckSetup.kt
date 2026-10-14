package es.sebas1705.owlsplit.appcheck

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds attest with the App Check debug provider. On first use it logs its debug token
 * (logcat tag `DebugAppCheckProvider`); add it in the Firebase console (App Check → apps → the
 * `.debug` app → Manage debug tokens) so this install keeps working once enforcement is on.
 */
internal object AppCheckSetup {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        // Ask for a token now, so the debug secret shows up in logcat at start-up.
        FirebaseAppCheck.getInstance().getAppCheckToken(false).addOnFailureListener {
            Log.w(TAG, "App Check debug token not accepted yet: register it in the console", it)
        }
    }

    private const val TAG = "OwlsplitAppCheck"
}
