package es.sebas1705.owlsplit.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import es.sebas1705.owlsplit.R
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the widget in step with the balances, and offers "add expense" as a launcher shortcut. */
object WidgetUpdates {
    @OptIn(FlowPreview::class) // debounce
    fun start(context: Context, home: ObserveHome, scope: CoroutineScope) {
        scope.launch {
            // Only what the widget shows: the balances per group, the currency and how many groups.
            home().map { data ->
                Triple(
                    data.preferredCurrency,
                    data.groups.map { it.myBalance },
                    data.otherCurrencies
                )
            }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MILLIS)
                .collect {
                    val placed = GlanceAppWidgetManager(context).getGlanceIds(BalanceWidget::class.java)
                    if (placed.isNotEmpty()) BalanceWidget().updateAll(context)
                }
        }
    }

    /** "Add expense" on a long press of the app icon: the same as the widget's button. */
    fun publishShortcut(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_add_expense))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(BalanceWidget.addExpenseIntent(context))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    private const val DEBOUNCE_MILLIS = 1_000L
    private const val SHORTCUT_ID = "add_expense"
}
