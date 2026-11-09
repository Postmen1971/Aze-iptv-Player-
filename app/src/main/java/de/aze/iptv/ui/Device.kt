package de.aze.iptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geräteart: bestimmt Bedienung (Fernbedienung oder Touch) und Größen der Oberfläche. */
enum class DeviceKind(val title: String) { TV("Fernseher"), TABLET("Tablet"), PHONE("Handy") }

/**
 * Erkennt beim Start selbst, worauf die App läuft:
 *  - Fernseher / TV-Box (Android TV, Google TV, Fire TV, SHIELD): Bedienung mit der Fernbedienung, keine Touch-Knöpfe
 *  - Tablet: Touch-Knöpfe, Drehen frei
 *  - Handy: Touch-Knöpfe, immer Querformat, kompakte Ansichten
 * In den Einstellungen (Aussehen → Gerät) lässt sich das von Hand festlegen – z. B. für TV-Boxen,
 * die sich als Tablet melden.
 */
object Device {
    /** was die App erkannt hat */
    var detected = DeviceKind.TV
        private set
    /** was gilt (erkannt oder von Hand gewählt) */
    var kind by mutableStateOf(DeviceKind.TV)
        private set

    val isTv: Boolean get() = kind == DeviceKind.TV
    val isTouch: Boolean get() = kind != DeviceKind.TV
    val isPhone: Boolean get() = kind == DeviceKind.PHONE

    /** Mehr Höhe für Zeilen und Knöpfe bei Touch (Finger brauchen mehr Platz als der Fokus der Fernbedienung) */
    val touchPad: Dp get() = if (isTouch) 6.dp else 0.dp

    /** Hinweistext passend zur Bedienung */
    fun hint(remote: String, touch: String): String = if (isTouch) touch else remote

    fun detect(context: Context): DeviceKind {
        val pm = context.packageManager
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        val tv = uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature("android.software.leanback") ||
            pm.hasSystemFeature("android.software.leanback_only") ||
            pm.hasSystemFeature("android.hardware.type.television") ||
            pm.hasSystemFeature("amazon.hardware.fire_tv") ||
            // Box ganz ohne Touch-Bildschirm
            !pm.hasSystemFeature("android.hardware.touchscreen")
        if (tv) return DeviceKind.TV
        return if (context.resources.configuration.smallestScreenWidthDp >= 600) DeviceKind.TABLET else DeviceKind.PHONE
    }

    /** mode: 0 = automatisch, 1 = Fernseher, 2 = Tablet, 3 = Handy */
    fun init(context: Context, mode: Int) {
        detected = runCatching { detect(context) }.getOrDefault(DeviceKind.TV)
        apply(mode)
    }

    fun apply(mode: Int) {
        kind = when (mode) {
            1 -> DeviceKind.TV
            2 -> DeviceKind.TABLET
            3 -> DeviceKind.PHONE
            else -> detected
        }
    }
}
