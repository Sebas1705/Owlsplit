package es.sebas1705.owlsplit.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import es.sebas1705.owlsplit.MainActivity
import es.sebas1705.owlsplit.R
import es.sebas1705.owlsplit.core.datastore.DisplaySettings
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.moneyFormatFor
import es.sebas1705.owlsplit.domain.usecases.fx.ConvertTotal
import es.sebas1705.owlsplit.domain.usecases.groups.HomeData
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * The home-screen widget: my balance across groups (as on Home, other currencies converted at the
 * latest rate) and a button that opens the app to add an expense. Everything comes from the local
 * database, so it shows offline too; the app refreshes it when the balances change ([WidgetUpdates]).
 */
class BalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        val tag = koin.get<DisplaySettings>().language.first().tag
        val text = localized(context, tag)
        val summary = withTimeoutOrNull(LOAD_TIMEOUT) {
            val home = koin.get<ObserveHome>()().first()
            val total = koin.get<ConvertTotal>()(home)
            Summary(home, total.total)
        }
        val format = moneyFormatFor(tag ?: Locale.getDefault().toLanguageTag())
        val content = Content(
            balance = text.getString(R.string.widget_balance),
            amount = summary?.let { format.format(it.total, Currency.of(it.home.preferredCurrency), showPlus = true) },
            owed = (summary?.total ?: 0) > 0,
            groups = summary?.home?.groups?.size?.let { count ->
                if (count == 0) {
                    text.getString(R.string.widget_no_groups)
                } else {
                    text.resources.getQuantityString(R.plurals.widget_groups, count, count)
                }
            },
            add = text.getString(R.string.widget_add),
        )
        provideContent { Widget(content, context) }
    }

    private class Summary(val home: HomeData, val total: Long)

    private class Content(
        val balance: String,
        val amount: String?,
        val owed: Boolean,
        val groups: String?,
        val add: String,
    )

    @Composable
    private fun Widget(content: Content, context: Context) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(SURFACE)
                .cornerRadius(20.dp)
                .padding(16.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Owlsplit", style = TextStyle(color = SECONDARY, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(6.dp))
            Text(content.balance, style = TextStyle(color = SECONDARY, fontSize = 12.sp))
            Text(
                content.amount ?: "—",
                style = TextStyle(
                    color = if (content.owed) POSITIVE else PRIMARY,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            content.groups?.let { Text(it, style = TextStyle(color = SECONDARY, fontSize = 12.sp), maxLines = 1) }
            Spacer(GlanceModifier.height(10.dp))
            Box(
                GlanceModifier
                    .fillMaxWidth()
                    .background(ACCENT)
                    .cornerRadius(18.dp)
                    .padding(vertical = 8.dp)
                    .clickable(actionStartActivity(addExpenseIntent(context))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+ ${content.add}",
                    style = TextStyle(color = ON_ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    companion object {
        /** The action MainActivity reads to open "add expense" (also the launcher shortcut's). */
        const val ACTION_ADD_EXPENSE = "es.sebas1705.owlsplit.action.ADD_EXPENSE"

        fun addExpenseIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).setAction(ACTION_ADD_EXPENSE)

        private val LOAD_TIMEOUT = 5.seconds

        // The brand tokens (docs/DESIGN_SYSTEM.md), light and dark.
        private val SURFACE = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF16181F))
        private val PRIMARY = ColorProvider(day = Color(0xFF0B0C10), night = Color(0xFFF5F6FA))
        private val SECONDARY = ColorProvider(day = Color(0xFF5B5F6B), night = Color(0xFF8A8E99))
        private val POSITIVE = ColorProvider(day = Color(0xFF087A54), night = Color(0xFF34C38F))
        private val ACCENT = ColorProvider(day = Color(0xFF3B4BF5), night = Color(0xFF3B4BF5))
        private val ON_ACCENT = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))

        /** Android resources in the app's language (Profile → language), or the device's. */
        private fun localized(context: Context, tag: String?): Context {
            if (tag == null) return context
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            return context.createConfigurationContext(configuration)
        }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
