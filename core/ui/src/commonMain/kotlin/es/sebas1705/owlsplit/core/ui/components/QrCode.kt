package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/**
 * [text] as a QR code. Always dark on white, also in the dark theme: cameras read it best,
 * and the white margin is the quiet zone scanners need.
 */
@Composable
fun QrCode(text: String, description: String, modifier: Modifier = Modifier) {
    val painter = rememberQrCodePainter(text) {
        errorCorrectionLevel = QrErrorCorrectionLevel.Medium
        colors {
            dark = QrBrush.solid(QR_DARK)
            light = QrBrush.solid(Color.White)
        }
    }
    Image(
        painter,
        description,
        modifier.aspectRatio(1f).background(Color.White, OwlsplitShapes.container).padding(16.dp),
    )
}

private val QR_DARK = Color(0xFF0B0D12)
