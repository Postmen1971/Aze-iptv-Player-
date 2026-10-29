package de.aze.iptv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PIN-Eingabe: Zifferntasten der Fernbedienung oder Tastenfeld (Steuerkreuz/Touch).
 * onEntered gibt false zurück, wenn die PIN falsch ist.
 */
@Composable
fun PinScreen(
    title: String,
    hint: String? = null,
    onEntered: (String) -> Boolean,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstKey.requestFocus() }
    }

    fun add(d: String) {
        if (pin.length >= 4) return
        pin += d
        error = null
        if (pin.length == 4) {
            if (!onEntered(pin)) {
                error = "Falsche PIN"
                pin = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.ScreenBrush)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                when (code) {
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        add((code - AndroidKeyEvent.KEYCODE_0).toString()); true
                    }
                    in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> {
                        add((code - AndroidKeyEvent.KEYCODE_NUMPAD_0).toString()); true
                    }
                    AndroidKeyEvent.KEYCODE_DEL -> {
                        pin = pin.dropLast(1); true
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(if (i < pin.length) Palette.Accent else Palette.Line, CircleShape),
                    )
                }
            }
            Text(error ?: hint ?: " ", color = if (error != null) Palette.Live else Palette.TextMuted, fontSize = 15.sp)

            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("←", "0", "✕"))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { c, label ->
                            Focusable(
                                modifier = if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier,
                                onClick = {
                                    when (label) {
                                        "←" -> pin = pin.dropLast(1)
                                        "✕" -> onCancel()
                                        else -> add(label)
                                    }
                                },
                            ) { focused ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 76.dp, height = 56.dp)
                                        .background(if (focused) Palette.Accent else Palette.Raised, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label,
                                        color = if (focused) Palette.OnAccent else Palette.Text,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
