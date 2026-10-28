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
import androidx.compose.ui.graphics.Color
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

/** Die vier Farbschemata. */
val Schemes = listOf(
    ColorScheme(
        "Mitternacht",
        ground = Color(0xFF0B0F16), panel = Color(0xFF111722), panelDark = Color(0xFF0D121B),
        raised = Color(0xFF1E2735), line = Color(0xFF263245),
        text = Color(0xFFF2F4F8), textSoft = Color(0xFFC9D1DD), textMuted = Color(0xFF8E99A8),
        accent = Color(0xFFE9EDF3), onAccent = Color(0xFF111722), highlight = Color(0xFF5AA0FF),
    ),
    ColorScheme(
        "Schiefer",
        ground = Color(0xFF2E3B42), panel = Color(0xFF37474F), panelDark = Color(0xFF2A353B),
        raised = Color(0xFF455A64), line = Color(0xFF52666F),
        text = Color(0xFFFFFFFF), textSoft = Color(0xFFD5DEE3), textMuted = Color(0xFFA9B8C0),
        accent = Color(0xFFECEFF1), onAccent = Color(0xFF263238), highlight = Color(0xFF4FC3F7),
    ),
    ColorScheme(
        "Ozean",
        ground = Color(0xFF0D3A74), panel = Color(0xFF15498F), panelDark = Color(0xFF0F3F7F),
        raised = Color(0xFF2160AE), line = Color(0xFF2E6BB8),
        text = Color(0xFFFFFFFF), textSoft = Color(0xFFDCE8F7), textMuted = Color(0xFFA8C3E6),
        accent = Color(0xFFD2E3F7), onAccent = Color(0xFF0D2E5A), highlight = Color(0xFFFFD54F),
    ),
    ColorScheme(
        "Petrol",
        ground = Color(0xFF12292F), panel = Color(0xFF173238), panelDark = Color(0xFF10242A),
        raised = Color(0xFF214249), line = Color(0xFF2A4D55),
        text = Color(0xFFFFFFFF), textSoft = Color(0xFFD2E0E3), textMuted = Color(0xFF9DB5BA),
        accent = Color(0xFFE6EEF0), onAccent = Color(0xFF12292F), highlight = Color(0xFF3AA6E0),
    ),
)

/** Farben der Oberfläche – folgen dem gewählten Farbschema. */
object Palette {
    var scheme by mutableStateOf(Schemes[0])

    val Ground get() = scheme.ground
    val Panel get() = scheme.panel
    val PanelDark get() = scheme.panelDark
    val Raised get() = scheme.raised
    val Line get() = scheme.line
    val Text get() = scheme.text
    val TextSoft get() = scheme.textSoft
    val TextMuted get() = scheme.textMuted
    val Accent get() = scheme.accent
    val OnAccent get() = scheme.onAccent
    val Highlight get() = scheme.highlight
    val Live = Color(0xFFD64545)

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
        val bg = when {
            focused -> Palette.Accent
            selected -> Palette.Raised
            else -> Palette.Panel
        }
        val fg = when {
            focused -> Palette.OnAccent
            danger -> Palette.Live
            else -> Palette.Text
        }
        Box(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .background(bg, RoundedCornerShape(10.dp))
                .then(if (selected && !focused) Modifier.border(2.dp, Palette.Accent, RoundedCornerShape(10.dp)) else Modifier)
                .padding(PaddingValues(horizontal = 14.dp, vertical = 7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = fg, fontSize = 14.sp, fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
