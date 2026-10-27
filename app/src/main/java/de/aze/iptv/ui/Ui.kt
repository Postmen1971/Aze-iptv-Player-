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
object Palette {
    val Ground = Color(0xFF0E1116)
    val Panel = Color(0xFF151A21)
    val PanelDark = Color(0xFF0B0E12)
    val Raised = Color(0xFF1F2630)
    val Line = Color(0xFF262D38)
    val Text = Color(0xFFEDEFF3)
    val TextSoft = Color(0xFFC7CDD6)
    val TextMuted = Color(0xFF9AA3AF)
    val Accent = Color(0xFFF2B544)
    val OnAccent = Color(0xFF15130E)
    val Live = Color(0xFFD64545)
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
                        } else if (!longFired && onLongClick != null) {
                            longFired = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (!longFired) onClick()
                        longFired = false
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
                .heightIn(min = 44.dp)
                .background(bg, RoundedCornerShape(10.dp))
                .then(if (selected && !focused) Modifier.border(2.dp, Palette.Accent, RoundedCornerShape(10.dp)) else Modifier)
                .padding(PaddingValues(horizontal = 18.dp, vertical = 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = fg, fontSize = 17.sp, fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
