package es.sebas1705.owlsplit.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest with Play Integrity. Nothing is enforced until the owner turns enforcement
 * on in the Firebase console (docs/FIREBASE.md#app-check); until then requests without a valid
 * token are only counted as unverified.
 */
internal object AppCheckSetup {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
