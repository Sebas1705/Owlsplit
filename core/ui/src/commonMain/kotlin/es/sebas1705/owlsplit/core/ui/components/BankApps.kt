package es.sebas1705.owlsplit.core.ui.components

/** A banking app that sends Bizum, by its Android package. */
data class BankApp(val id: String, val name: String)

/**
 * The banking apps on this phone, to open the one used for Bizum after copying the phone number (Bizum
 * has no public link). Android tells which are installed; iOS cannot, so there it offers none.
 */
interface BankApps {
    /** The ones installed, in [SPANISH_BANKS] order. */
    fun installed(): List<BankApp>

    /** Opens it; false if it is no longer installed. */
    fun open(app: BankApp): Boolean

    /** The one chosen last time, while it is still installed. */
    fun preferred(): BankApp?

    fun remember(app: BankApp)

    companion object {
        /**
         * Spanish banks with Bizum, by package name as published on Google Play (each one checked on
         * 2026-10-16). The Android manifest lists the same packages in <queries>, or Android 11+ hides them.
         */
        val SPANISH_BANKS: List<BankApp> = listOf(
            BankApp("com.bbva.bbvacontigo", "BBVA"),
            BankApp("es.lacaixa.mobile.android.newwapicon", "CaixaBank"),
            BankApp("es.bancosantander.apps", "Santander"),
            BankApp("net.inverline.bancosabadell.officelocator.android", "Sabadell"),
            BankApp("www.ingdirect.nativeframe", "ING"),
            BankApp("com.imaginbank.app", "imagin"),
            BankApp("com.bankinter.launcher", "Bankinter"),
            BankApp("es.openbank.mobile", "Openbank"),
            BankApp("es.unicajabanco.app", "Unicaja"),
            BankApp("com.abanca.bancamovil.particulares", "ABANCA"),
            BankApp("com.kutxabank.android", "Kutxabank"),
            BankApp("es.ibercaja.ibercajaapp", "Ibercaja"),
            BankApp("com.grupocajamar.wefferent", "Cajamar"),
            BankApp("com.rsi.nba", "Ruralvía"),
            BankApp("com.tecnocom.cajalaboral", "Laboral Kutxa"),
            BankApp("es.evobanco.bancamovil", "EVO Banco"),
            BankApp("com.myinvestor.es", "MyInvestor"),
            BankApp("es.eurocajarural.EurocajaRural", "Eurocaja Rural"),
            BankApp("es.caixaontinyent.caixaontinyentapp", "Caixa Ontinyent"),
            BankApp("com.mediolanum.android.fullbanca", "Mediolanum"),
        )

        /** The installed ones among [SPANISH_BANKS], given how to tell whether a package is installed. */
        fun installedAmong(isInstalled: (String) -> Boolean): List<BankApp> =
            SPANISH_BANKS.filter { isInstalled(it.id) }

        /** The remembered one if it is still installed; with a single bank installed, that one. */
        fun preferredAmong(installed: List<BankApp>, rememberedId: String?): BankApp? =
            installed.firstOrNull { it.id == rememberedId } ?: installed.singleOrNull()
    }
}

/** No banking apps to offer (iOS, previews and tests): the phone number is still copied. */
object NoBankApps : BankApps {
    override fun installed(): List<BankApp> = emptyList()

    override fun open(app: BankApp): Boolean = false

    override fun preferred(): BankApp? = null

    override fun remember(app: BankApp) = Unit
}
