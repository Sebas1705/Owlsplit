package es.sebas1705.owlsplit

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Nothing leaves the device through Auto Backup or device transfer (docs/FIREBASE.md, "Backup and device
 * transfer"): the Auth session, the Keystore-bound encrypted prefs, the profile and the database with its
 * outbox all belong to one installation.
 */
class BackupRulesTest {
    private val main = File("src/androidMain")

    private fun parse(file: File): Document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private val application: Element
        get() = parse(File(main, "AndroidManifest.xml")).getElementsByTagName("application").item(0) as Element

    private fun Element.androidAttr(name: String) = getAttributeNS(ANDROID_NS, name)

    /** The `exclude` domains (with path ".") and the number of `include`s under [element]. */
    private fun rules(element: Element): Pair<Set<String>, Int> {
        val excludes = element.getElementsByTagName("exclude")
        val domains = (0 until excludes.length).map { excludes.item(it) as Element }
            .onEach { assertEquals(".", it.getAttribute("path"), "every exclude covers the whole domain") }
            .map { it.getAttribute("domain") }
            .toSet()
        return domains to element.getElementsByTagName("include").length
    }

    private fun xml(reference: String): File {
        assertEquals("@xml/", reference.take(5), "a resource reference: $reference")
        return File(main, "res/xml/${reference.removePrefix("@xml/")}.xml")
    }

    @Test
    fun cloudBackupIsOff() = assertEquals("false", application.androidAttr("allowBackup"))

    @Test
    fun dataExtractionRulesExcludeEverything() {
        val root = parse(xml(application.androidAttr("dataExtractionRules"))).documentElement
        assertEquals("data-extraction-rules", root.tagName)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = root.getElementsByTagName(section).item(0) as Element
            assertEquals(ALL_DOMAINS to 0, rules(element), section)
        }
    }

    @Test
    fun fullBackupContentExcludesEverything() {
        val root = parse(xml(application.androidAttr("fullBackupContent"))).documentElement
        assertEquals("full-backup-content", root.tagName)
        assertEquals(ALL_DOMAINS to 0, rules(root))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        /**
         * Every domain: the backup agent walks files/, databases/ and shared_prefs/ as domains of their own, so
         * excluding `root` alone still backs them up (checked on an API 36 emulator).
         */
        val ALL_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
