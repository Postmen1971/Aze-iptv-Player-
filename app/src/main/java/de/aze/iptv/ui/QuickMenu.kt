package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Ein Eintrag im Schnellmenü: Titel, aktueller Wert, und was OK macht (nächster Wert). */
class QuickItem(val title: String, val value: String, val onNext: () -> Unit)

/**
 * Durchsichtige Leiste unten im Bild.
 * Links/Rechts wählt den Punkt, OK schaltet den Wert weiter. Schließt sich nach 6 Sekunden ohne Eingabe.
 */
@Composable
fun QuickMenu(items: List<QuickItem>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var touched by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    LaunchedEffect(touched) {
        delay(6_000)
        onClose()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .padding(bottom = 10.dp, start = 12.dp, end = 12.dp)
                .horizontalScroll(rememberScrollState())
                .background(Palette.Ground.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items.forEachIndexed { i, item ->
                Focusable(
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = {
                        item.onNext()
                        touched++
                    },
                    onFocus = { touched++ },
                ) { focused ->
                    Column(
                        modifier = Modifier
                            .widthIn(min = 78.dp, max = 160.dp)
                            .background(
                                if (focused) Palette.Accent else Palette.Raised.copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            item.title,
                            color = if (focused) Palette.OnAccent else Palette.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            item.value,
                            color = if (focused) Palette.OnAccent else Palette.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
