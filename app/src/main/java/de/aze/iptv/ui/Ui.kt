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

/** Die Farbschemata (alle dunkel, mit unterschiedlicher Grundfarbe). */
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
        ground = Color(0xFF07152B), panel = Color(0xFF0C1E3B), panelDark = Color(0xFF091932),
        raised = Color(0xFF14305A), line = Color(0xFF1B3966),
        text = Color(0xFFFFFFFF), textSoft = Color(0xFFC6D4E8), textMuted = Color(0xFF8A9FBF),
        accent = Color(0xFFD5E3F6), onAccent = Color(0xFF07152B), highlight = Color(0xFF4FA3FF),
    ),
    ColorScheme(
        "Petrol",
        ground = Color(0xFF12292F), panel = Color(0xFF173238), panelDark = Color(0xFF10242A),
        raised = Color(0xFF214249), line = Color(0xFF2A4D55),
        text = Color(0xFFFFFFFF), textSoft = Color(0xFFD2E0E3), textMuted = Color(0xFF9DB5BA),
        accent = Color(0xFFE6EEF0), onAccent = Color(0xFF12292F), highlight = Color(0xFF3AA6E0),
    ),
    ColorScheme(
        "Graphit",
        ground = Color(0xFF121314), panel = Color(0xFF1A1C1E), panelDark = Color(0xFF151618),
        raised = Color(0xFF2A2D31), line = Color(0xFF33373C),
        text = Color(0xFFF1F2F3), textSoft = Color(0xFFC4C8CD), textMuted = Color(0xFF8B9096),
        accent = Color(0xFFE8EAED), onAccent = Color(0xFF151618), highlight = Color(0xFFFF9F43),
    ),
    ColorScheme(
        "Smaragd",
        ground = Color(0xFF0A1C17), panel = Color(0xFF0F2620), panelDark = Color(0xFF0C211B),
        raised = Color(0xFF173A31), line = Color(0xFF1E463B),
        text = Color(0xFFF0F7F4), textSoft = Color(0xFFC5DBD3), textMuted = Color(0xFF8BA89E),
        accent = Color(0xFFDDF0E8), onAccent = Color(0xFF0A1C17), highlight = Color(0xFF3DDC97),
    ),
    ColorScheme(
        "Aubergine",
        ground = Color(0xFF170F22), panel = Color(0xFF20152F), panelDark = Color(0xFF1B1228),
        raised = Color(0xFF2F2145), line = Color(0xFF3A2A54),
        text = Color(0xFFF5F0FA), textSoft = Color(0xFFD4C8E3), textMuted = Color(0xFF9D8DB5),
        accent = Color(0xFFE9E0F5), onAccent = Color(0xFF170F22), highlight = Color(0xFFB98CFF),
    ),
    ColorScheme(
        "Bordeaux",
        ground = Color(0xFF1C0D10), panel = Color(0xFF261216), panelDark = Color(0xFF201013),
        raised = Color(0xFF3A1C22), line = Color(0xFF47232A),
        text = Color(0xFFFAF1F2), textSoft = Color(0xFFE0C9CC), textMuted = Color(0xFFAE8E93),
        accent = Color(0xFFF4E3E5), onAccent = Color(0xFF1C0D10), highlight = Color(0xFFFF6B7A),
    ),
    ColorScheme(
        "Kupfer",
        ground = Color(0xFF1A130E), panel = Color(0xFF241A13), panelDark = Color(0xFF1F1610),
        raised = Color(0xFF36271C), line = Color(0xFF433123),
        text = Color(0xFFFAF4EE), textSoft = Color(0xFFE0D0C2), textMuted = Color(0xFFAD9682),
        accent = Color(0xFFF3E6DA), onAccent = Color(0xFF1A130E), highlight = Color(0xFFE8964A),
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
