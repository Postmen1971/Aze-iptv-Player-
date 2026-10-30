package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController
import de.aze.iptv.data.Channel
import de.aze.iptv.data.Epg
import de.aze.iptv.data.EpgChannel

fun msLabel(ms: Int): String = when {
    ms > 0 -> "+$ms ms"
    ms < 0 -> "$ms ms"
    else -> "0 ms"
}

/**
 * Ton-Bild-Synchron für EINEN Sender (aus dem Schnellmenü).
 * Wirkt zusätzlich zum allgemeinen Wert aus den Einstellungen.
 */
@Composable
fun AudioSyncPanel(app: AppController, channel: Channel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val own = app.channelDelay(channel.key)
    fun set(v: Int) = app.updateChannelDelay(channel.key, v)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .padding(bottom = 18.dp, start = 12.dp, end = 12.dp)
                .widthIn(max = 760.dp)
                .background(Palette.PanelBrush, shape)
                .border(1.dp, Palette.Raised, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Ton-Bild-Synchron · ${channel.name}", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(msLabel(own), color = Palette.Highlight, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "nur dieser Sender · allgemein ${msLabel(app.audioDelay)} · wirksam ${msLabel(app.effectiveDelay(channel.key))}",
                    color = Palette.TextSoft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton("− 100", modifier = Modifier.focusRequester(first), onClick = { set(own - 100) })
                TvButton("− 20", onClick = { set(own - 20) })
                TvButton("+ 20", onClick = { set(own + 20) })
                TvButton("+ 100", onClick = { set(own + 100) })
                TvButton("0", onClick = { set(0) })
                TvButton("Fertig", onClick = onClose)
            }
            Text(
                "Plus = Ton kommt später (wenn die Lippen dem Ton hinterherhinken). Minus = Ton kommt früher. Wird für diesen Sender gespeichert.",
                color = Palette.TextMuted, fontSize = 12.sp,
            )
        }
    }
}

/** Wörter eines Sendernamens für den Vergleich („TR: Show TV HD“ → [show, tv]). */
private fun words(s: String): List<String> =
    Epg.normName(s).let { listOf(it) } +
        s.lowercase().split(Regex("[^a-z0-9äöüßçğış]+")).filter { it.length >= 2 && it !in setOf("hd", "fhd", "uhd", "sd", "tr", "de", "tv") }

private fun score(ch: String, e: EpgChannel): Int {
    val a = Epg.normName(ch)
    val b = Epg.normName(e.name)
    if (a.isNotEmpty() && a == b) return 100
    var s = 0
    if (a.isNotEmpty() && (b.startsWith(a) || a.startsWith(b))) s += 40
    val wa = words(ch).drop(1)
    val wb = (words(e.name).drop(1) + e.id.lowercase().split('.', '_', '-', ' ')).toSet()
    s += wa.count { it in wb } * 15
    return s
}

/**
 * EPG manuell zuordnen: Liste aller Sender aus den EPG-Quellen, passende zuerst.
 */
@Composable
fun EpgMapDialog(app: AppController, channel: Channel, onClose: () -> Unit) {
    var search by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<EpgChannel>?>(null) }
    BackHandler(enabled = search == null, onBack = onClose)

    val loading = app.epgLoading
    LaunchedEffect(loading) {
        if (!loading) {
            val c = app.epgCatalog()
            catalog = c
            // Noch keine Liste (z. B. direkt nach dem Update): einmal laden
            if (c.isEmpty()) app.refreshEpg()
        }
    }
    val current = app.epgMap[channel.key]
    val autoId = remember(app.epg) { app.epg.idFor(channel) }
    val shown = remember(catalog, filter) {
        val all = catalog.orEmpty()
        if (filter.isNotBlank()) {
            val f = filter.lowercase()
            all.filter { it.name.lowercase().contains(f) || it.id.lowercase().contains(f) }.take(300)
        } else {
            all.asSequence().map { it to score(channel.name, it) }.filter { it.second > 0 }
                .sortedByDescending { it.second }.map { it.first }.take(150).toList()
        }
    }

    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ground.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 820.dp)
                .fillMaxHeight(0.9f)
                .background(Palette.PanelBrush, shape)
                .border(1.dp, Palette.Raised, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Programm zuordnen · ${channel.name}", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    current != null -> "Eigene Zuordnung: $current"
                    autoId != null -> "Automatisch gefunden: $autoId"
                    else -> "Noch kein Programm gefunden."
                } + " · ${app.epgReport.ifEmpty { "EPG-Quellen unter Einstellungen → Programm (EPG)" }}",
                color = Palette.TextSoft, fontSize = 13.sp,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(if (filter.isBlank()) "Suchen …" else "Suche: $filter", modifier = Modifier.focusRequester(first), onClick = { search = filter })
                if (filter.isNotBlank()) TvButton("Vorschläge", onClick = { filter = "" })
                TvButton("Automatisch", selected = current == null, onClick = {
                    app.setEpgMapping(channel, null)
                    onClose()
                })
                TvButton("Schließen", onClick = onClose)
            }
            when {
                catalog == null || (loading && catalog.isNullOrEmpty()) ->
                    Text("EPG-Sender werden geladen …", color = Palette.TextMuted, fontSize = 14.sp)
                catalog.isNullOrEmpty() ->
                    Text("Keine EPG-Quelle geladen. Unter Einstellungen → Programm (EPG) eine Quelle hinzufügen.", color = Palette.TextMuted, fontSize = 14.sp)
                shown.isEmpty() ->
                    Text(if (filter.isBlank()) "Kein ähnlicher Name gefunden – über „Suchen …“ den Sender suchen." else "Nichts gefunden.", color = Palette.TextMuted, fontSize = 14.sp)
                else -> LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(shown, key = { it.id }) { e ->
                        Focusable(onClick = {
                            app.setEpgMapping(channel, e.id)
                            onClose()
                        }) { focused ->
                            val sel = e.id == current || (current == null && e.id == autoId)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (focused) Palette.FocusBrush else androidx.compose.ui.graphics.SolidColor(if (sel) Palette.Raised else androidx.compose.ui.graphics.Color.Transparent),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(if (sel) "●" else " ", color = if (focused) Palette.OnAccent else Palette.Highlight, fontSize = 14.sp)
                                Text(e.name, color = if (focused) Palette.OnAccent else Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(e.id, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    search?.let { s ->
        InputDialog(
            label = "Sendername oder Kennung suchen",
            initial = s,
            password = false,
            keyboard = KeyboardType.Text,
            onDone = {
                filter = it.trim()
                search = null
            },
            onCancel = { search = null },
        )
    }
}
