package es.sebas1705.owlsplit.share

import android.content.Intent
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.core.ui.components.TextSharer

/** The system share sheet, opened from the activity in front. */
class AndroidTextSharer(private val activities: ResumedActivity) : TextSharer {
    override fun share(text: String): Boolean {
        val activity = activities.current ?: return false
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(send, null))
        return true
    }
}
