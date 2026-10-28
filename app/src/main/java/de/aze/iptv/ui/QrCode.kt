package de.aze.iptv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** QR-Code zum Abscannen mit der Handy-Kamera (öffnet die Adresse direkt im Browser). */
@Composable
fun QrCode(text: String, size: Dp = 150.dp) {
    val bmp = remember(text) {
        runCatching {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
            val bitmap = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
            for (y in 0 until m.height) for (x in 0 until m.width) {
                bitmap.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            bitmap.asImageBitmap()
        }.getOrNull()
    } ?: return
    Image(
        bitmap = bmp,
        contentDescription = "QR-Code",
        filterQuality = FilterQuality.None,
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(6.dp)
            .size(size),
    )
}
