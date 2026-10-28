package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController
import de.aze.iptv.data.NumberedChannel
import java.text.Normalizer

/** Vereinfacht Text für die Suche: klein, ohne Akzente, türkische Buchstaben wie ihre Grundform. */
private fun simplify(s: String): String {
    val t = s.lowercase()
        .replace('ı', 'i').replace('ç', 'c').replace('ğ', 'g')
        .replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
    return Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/**
 * Sendersuche: Name (ohne Rücksicht auf Groß/Klein und ç, ş, ü …) oder Nummer.
 * OK = umschalten, OK lang = Favorit an/aus.
 */
@Composable
fun SearchOverlay(app: AppController, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var query by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    // Suchtexte einmal vorbereiten und suchen – im Hintergrund, damit die Eingabe bei großen Listen nicht hakt.
    val lineup = app.lineup
    val index by androidx.compose.runtime.produceState<List<Pair<NumberedChannel, String>>>(emptyList(), lineup) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            lineup.all.map { it to simplify(it.channel.name + " " + it.channel.group) }
        }
    }
    val q = simplify(query.trim())
    val results by androidx.compose.runtime.produceState<List<NumberedChannel>>(emptyList(), q, index) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            when {
                q.isEmpty() -> emptyList()
                q.all { it.isDigit() } -> index.filter { it.first.number.toString().startsWith(q) }.map { it.first }
                else -> {
                    val words = q.split(' ').filter { it.isNotBlank() }
                    index.filter { (_, text) -> words.all { text.contains(it) } }.map { it.first }
                }
            }.take(300)
        }
    }
    val currentKey = app.current?.channel?.key

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 300.dp)
                .fillMaxWidth(0.36f)
                .background(Palette.Panel.copy(alpha = 0.97f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sender suchen", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TvButton("Schließen", onClick = onClose)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Name oder Nummer, z. B. trt oder 12", color = Palette.TextMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text,
                    focusedBorderColor = Palette.Accent,
                    unfocusedBorderColor = Palette.Line,
                    cursorColor = Palette.Accent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(field),
            )
            Text(
                when {
                    q.isEmpty() -> "Tippe einen Namen oder eine Nummer."
                    results.isEmpty() -> "Nichts gefunden."
                    results.size >= 300 -> "Mehr als 300 Treffer – bitte genauer suchen."
                    else -> "${results.size} Treffer"
                },
                color = Palette.TextMuted, fontSize = 15.sp,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(results, key = { it.channel.key }) { nc ->
                    val playing = nc.channel.key == currentKey
                    Focusable(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            app.play(nc)
                            onClose()
                        },
                        onLongClick = { app.toggleFavorite(nc.channel) },
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(if (focused) Palette.Accent else Palette.Panel.copy(alpha = 0f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${nc.number}",
                                color = if (focused) Palette.OnAccent else if (playing) Palette.Accent else Palette.TextSoft,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End, modifier = Modifier.width(40.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    nc.channel.name,
                                    color = if (focused) Palette.OnAccent else Palette.Text,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    nc.channel.group,
                                    color = if (focused) Palette.OnAccent else Palette.TextMuted,
                                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (nc.favorite) Text("★", color = if (focused) Palette.OnAccent else Palette.Accent, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClose))
    }
}
