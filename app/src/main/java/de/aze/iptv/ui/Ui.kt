package de.aze.iptv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Farben aus den Entwürfen. */
/** Ein Farbschema der App. */
class ColorScheme(
    val name: String,
    val ground: Color,
    val panel: Color,
    val panelDark: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val textSoft: Color,
    val textMuted: Color,
    /** Auswahl (Fokus) – hell hinterlegt */
    val accent: Color,
    val onAccent: Color,
    /** Hervorhebung: laufender Sender, Fortschritt */
    val highlight: Color,
)

/** Die Farbschemata: dunkel, ruhig, mit dezenten Verläufen (angelehnt an moderne TV-Apps). */
val Schemes = listOf(
    ColorScheme(
        "Mitternacht",
        ground = Color(0xFF0B0F16), panel = Color(0xFF131A26), panelDark = Color(0xFF0D121B),
        raised = Color(0xFF212B3B), line = Color(0xFF2A3549),
        text = Color(0xFFF2F4F8), textSoft = Color(0xFFC9D1DD), textMuted = Color(0xFF8E99A8),
        accent = Color(0xFFE9EDF3), onAccent = Color(0xFF111722), highlight = Color(0xFF5AA0FF),
    ),
    ColorScheme(
        "Ozean",
        ground = Color(0xFF07152B), panel = Color(0xFF0E2242), panelDark = Color(0xFF091932),
        raised = Color(0xFF173563), line = Color(0xFF1E3D6C),
        text = Color(0xFFFFFFFF), textSoft = Color(0xFFC6D4E8), textMuted = Color(0xFF8A9FBF),
        accent = Color(0xFFD5E3F6), onAccent = Color(0xFF07152B), highlight = Color(0xFF4FA3FF),
    ),
    ColorScheme(
        "Graphit",
        ground = Color(0xFF121314), panel = Color(0xFF1C1E21), panelDark = Color(0xFF151618),
        raised = Color(0xFF2C3035), line = Color(0xFF35393F),
        text = Color(0xFFF1F2F3), textSoft = Color(0xFFC4C8CD), textMuted = Color(0xFF8B9096),
        accent = Color(0xFFE8EAED), onAccent = Color(0xFF151618), highlight = Color(0xFFFF9F43),
    ),
    ColorScheme(
        "Kobalt",
        ground = Color(0xFF0A0E24), panel = Color(0xFF141A3D), panelDark = Color(0xFF0E1330),
        raised = Color(0xFF222B5C), line = Color(0xFF2B3569),
        text = Color(0xFFF4F5FF), textSoft = Color(0xFFCBD0F0), textMuted = Color(0xFF8E95C2),
        accent = Color(0xFFE3E7FF), onAccent = Color(0xFF0E1330), highlight = Color(0xFF7C8CFF),
    ),
    ColorScheme(
        "Stahl",
        ground = Color(0xFF0E1318), panel = Color(0xFF17202A), panelDark = Color(0xFF111820),
        raised = Color(0xFF223040), line = Color(0xFF2A394B),
        text = Color(0xFFF0F5F9), textSoft = Color(0xFFC3D0DC), textMuted = Color(0xFF8798A9),
        accent = Color(0xFFE2EEF7), onAccent = Color(0xFF111820), highlight = Color(0xFF38BDF8),
    ),
    ColorScheme(
        "Schwarz",
        ground = Color(0xFF000000), panel = Color(0xFF111111), panelDark = Color(0xFF080808),
        raised = Color(0xFF1F1F1F), line = Color(0xFF2A2A2A),
        text = Color(0xFFFFFFFF), textSoft = Color(0xFFCCCCCC), textMuted = Color(0xFF8C8C8C),
        accent = Color(0xFFF2F2F2), onAccent = Color(0xFF0A0A0A), highlight = Color(0xFFE50914),
    ),
)

/** Wählbare Schriftfarben (null = wie im Farbschema). */
val TextColors: List<Pair<String, Color?>> = listOf(
    "Standard" to null,
    "Reinweiß" to Color(0xFFFFFFFF),
    "Warmweiß" to Color(0xFFFFF3DD),
    "Hellblau" to Color(0xFFD8E8FF),
    "Gelb" to Color(0xFFFFE27A),
)

/** Farben der Oberfläche – folgen dem gewählten Farbschema. */
object Palette {
    var scheme by mutableStateOf(Schemes[0])

    val Ground get() = scheme.ground
    val Panel get() = scheme.panel
    val PanelDark get() = scheme.panelDark
    val Raised get() = scheme.raised
    val Line get() = scheme.line
    /** Eigene Schriftfarbe (Einstellungen → Darstellung) */
    var textOverride by mutableStateOf<Color?>(null)
    /** Laufende Sendung (EPG): dezentes Gelb, hebt sich vom Sendernamen ab */
    val EpgText = Color(0xFFDCC27A)
    val Text get() = textOverride ?: scheme.text
    val TextSoft get() = scheme.textSoft
    val TextMuted get() = scheme.textMuted
    val Accent get() = scheme.accent
    val OnAccent get() = scheme.onAccent
    val Highlight get() = scheme.highlight
    val Live = Color(0xFFD64545)

    // ---- Dezente Verläufe ----
    /** Bildschirm-Hintergrund: oben etwas heller */
    val ScreenBrush get() = Brush.verticalGradient(listOf(lerp(scheme.ground, scheme.raised, 0.45f), scheme.ground, scheme.ground))
    /** Seitenleisten und Kopfzeilen */
    val PanelBrush get() = Brush.verticalGradient(listOf(lerp(scheme.panel, scheme.raised, 0.35f), scheme.panelDark))
    /** Ebenen über dem laufenden Bild (Senderliste, Menü) */
    fun overlayBrush(alpha: Float = 0.94f) = Brush.horizontalGradient(
        listOf(scheme.panelDark.copy(alpha = alpha), lerp(scheme.panel, scheme.raised, 0.25f).copy(alpha = alpha * 0.95f)),
    )
    /** Gruppenübersicht: leicht im Akzentton */
    val GroupBrush get() = Brush.verticalGradient(
        listOf(lerp(scheme.panel, scheme.highlight, 0.12f).copy(alpha = 0.97f), scheme.panelDark.copy(alpha = 0.97f)),
    )
    /** Fokus: heller Verlauf */
    val FocusBrush get() = Brush.verticalGradient(listOf(scheme.accent, lerp(scheme.accent, scheme.highlight, 0.22f)))

    // Gruppenübersicht: etwas heller abgesetzt im gleichen Farbton
    val GroupBlue get() = scheme.panel.copy(alpha = 0.96f)
    val GroupSelect get() = scheme.accent
    val GroupActive get() = scheme.raised
    val GroupSoft get() = scheme.textMuted
}

fun isOkKey(key: Key): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * Fokussierbares Element für Fernbedienung und Touch.
 * OK kurz = onClick, OK lang gedrückt = onLongClick. Bei Touch: Tippen bzw. lange Tippen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    // Nur auf OK reagieren, wenn der Tastendruck auch hier begonnen hat
    // (sonst würde das Loslassen eines langen OK, das ein Menü geöffnet hat, sofort etwas auslösen).
    var downSeen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .onPreviewKeyEvent { e ->
                if (!isOkKey(e.key)) return@onPreviewKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            longFired = false
                            downSeen = true
                        } else if (downSeen && !longFired && onLongClick != null) {
                            longFired = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (downSeen && !longFired) onClick()
                        longFired = false
                        downSeen = false
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        content(focused)
    }
}

/** Einfache Schaltfläche im Stil der Entwürfe. */
@Composable
fun TvButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Focusable(modifier = modifier, onClick = onClick) { focused ->
        val shape = RoundedCornerShape(12.dp)
        val fg = when {
            focused -> Palette.OnAccent
            danger -> Palette.Live
            else -> Palette.Text
        }
        Box(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .then(
                    when {
                        focused -> Modifier.background(Palette.FocusBrush, shape)
                        selected -> Modifier
                            .background(Palette.Highlight.copy(alpha = 0.20f), shape)
                            .border(1.5.dp, Palette.Highlight.copy(alpha = 0.85f), shape)
                        else -> Modifier
                            .background(Palette.Panel.copy(alpha = 0.75f), shape)
                            .border(1.dp, Palette.Line.copy(alpha = 0.7f), shape)
                    },
                )
                .padding(PaddingValues(horizontal = 14.dp, vertical = 7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = fg, fontSize = 14.sp, fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
