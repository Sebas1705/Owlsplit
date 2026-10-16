package es.sebas1705.owlsplit.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankAppsTest {
    private val bbva = "com.bbva.bbvacontigo"
    private val ing = "www.ingdirect.nativeframe"

    @Test
    fun theCatalogHasEachBankOnceWithAPackageName() {
        val ids = BankApps.SPANISH_BANKS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""").matches(it) })
        assertEquals(BankApps.SPANISH_BANKS.size, BankApps.SPANISH_BANKS.map { it.name }.toSet().size)
    }

    @Test
    fun onlyTheInstalledOnesAreOfferedInCatalogOrder() {
        val installed = BankApps.installedAmong { it == ing || it == bbva }
        assertEquals(listOf("BBVA", "ING"), installed.map { it.name })
        assertTrue(BankApps.installedAmong { false }.isEmpty())
    }

    @Test
    fun theRememberedBankWinsAndASingleOneNeedsNoChoice() {
        val both = BankApps.installedAmong { it == ing || it == bbva }
        assertEquals(ing, BankApps.preferredAmong(both, ing)?.id)
        // Two installed and none chosen (or the chosen one was uninstalled): the user picks.
        assertNull(BankApps.preferredAmong(both, null))
        assertNull(BankApps.preferredAmong(both, "es.openbank.mobile"))
        val one = BankApps.installedAmong { it == bbva }
        assertEquals(bbva, BankApps.preferredAmong(one, null)?.id)
    }
}
