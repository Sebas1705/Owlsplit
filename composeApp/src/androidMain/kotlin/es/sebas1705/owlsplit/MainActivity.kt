package es.sebas1705.owlsplit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.navigation.IncomingInvites

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity (rotation, process death) must not reopen a link already handled.
        if (savedInstanceState == null) handleLink(intent)
        setContent { App(modifier = Modifier.fillMaxSize()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString?.let(IncomingInvites::offer)
    }
}
