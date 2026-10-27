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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Farben im Stil klassischer Enigma2-Oberflächen: dunkles Blau, helle Markierung. */
private object E2 {
    val Panel = androidx.compose.ui.graphics.Color(0xF0101828)
    val Bar = androidx.compose.ui.graphics.Color(0xFF1C2C4C)
    val Select = androidx.compose.ui.graphics.Color(0xFF2F64C8)
    val Text = androidx.compose.ui.graphics.Color(0xFFF2F4F8)
    val Soft = androidx.compose.ui.graphics.Color(0xFFB8C2D6)
    val Gold = androidx.compose.ui.graphics.Color(0xFFF2B544)
}

/**
 * Senderliste im Enigma2-Stil:
 *  - öffnet direkt im Bouquet des laufenden Senders
 *  - Hoch/Runter: ein Sender, Links/Rechts: eine Seite weiter/zurück
 *  - OK: umschalten, OK lang: Favorit an/aus
 *  - Zurück (oder Menü): Bouquet-Übersicht; dort OK öffnet das Bouquet, Zurück schließt
 */
@Composable
fun BouquetOverlay(app: AppController, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val filter = app.playlistFilter
    val multi = app.sources.size > 1
    val bouquets = app.lineup.groups.filter {
        (it.channels.isNotEmpty() || it.isFavorites) && (it.isFavorites || filter == null || it.sourceId == filter)
    }
    val currentKey = app.current?.channel?.key
    val startBouquet = bouquets.indexOfFirst { g -> g.channels.any { it.channel.key == currentKey } }.coerceAtLeast(0)

    var bouquetIndex by remember { mutableIntStateOf(startBouquet) }
    var showBouquets by remember { mutableStateOf(false) }
    var openCount by remember { mutableIntStateOf(0) } // erstes Öffnen: auf laufenden Sender springen

    BackHandler {
        if (showBouquets) onClose() else showBouquets = true
    }

    val bouquet = bouquets.getOrNull(bouquetIndex)
    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.62f)
                .background(E2.Panel),
        ) {
            // Titelleiste
            Row(
                modifier = Modifier.fillMaxWidth().background(E2.Bar).padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (showBouquets) "Bouquets (TV)" else (bouquet?.name ?: "") + " (TV)",
                        color = E2.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (!showBouquets && multi && bouquet?.sourceName != null) {
                        Text(bouquet.sourceName, color = E2.Soft, fontSize = 13.sp)
                    }
                }
                if (multi) {
                    Text("Playlist: ${app.playlistName(filter)}", color = E2.Soft, fontSize = 14.sp)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (showBouquets) {
                    key("bouquets") {
                        val entries = bouquets.map { b ->
                            Triple(b.name + (if (multi && filter == null && b.sourceName != null) "  ·  ${b.sourceName}" else ""), "${b.channels.size}", b.isFavorites)
                        } + Triple("⚙  Einstellungen", "", false)
                        PagedList(
                            count = entries.size,
                            startIndex = bouquetIndex.coerceAtMost(entries.size - 1),
                            scope = scope,
                            onMenu = { showBouquets = false },
                        ) { index, requester, onFocused ->
                            val (title, right, fav) = entries[index]
                            Focusable(
                                modifier = Modifier.fillMaxWidth().focusRequester(requester),
                                onFocus = onFocused,
                                onClick = {
                                    if (index == bouquets.size) {
                                        onOpenSettings()
                                    } else {
                                        bouquetIndex = index
                                        showBouquets = false
                                        openCount++
                                    }
                                },
                            ) { focused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(if (focused) E2.Select else E2.Panel.copy(alpha = 0f))
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        (if (fav) "★ " else "") + title,
                                        color = E2.Text, fontSize = 20.sp,
                                        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(right, color = if (focused) E2.Text else E2.Soft, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                } else if (bouquet == null || bouquet.channels.isEmpty()) {
                    Text(
                        if (bouquet?.isFavorites == true) "Noch keine Favoriten. In einem Bouquet OK lang drücken, um einen Sender hinzuzufügen."
                        else "Keine Sender",
                        color = E2.Soft, fontSize = 18.sp, modifier = Modifier.padding(16.dp),
                    )
                    LaunchedEffect(Unit) { showBouquets = true }
                } else {
                    key(bouquetIndex, openCount) {
                        val start = bouquet.channels.indexOfFirst { it.channel.key == currentKey }.coerceAtLeast(0)
                        PagedList(
                            count = bouquet.channels.size,
                            startIndex = start,
                            scope = scope,
                            onMenu = { showBouquets = true },
                        ) { index, requester, onFocused ->
                            val nc = bouquet.channels[index]
                            val playing = nc.channel.key == currentKey
                            Focusable(
                                modifier = Modifier.fillMaxWidth().focusRequester(requester),
                                onFocus = onFocused,
                                onClick = {
                                    app.play(nc)
                                    onClose()
                                },
                                onLongClick = { app.toggleFavorite(nc.channel) },
                            ) { focused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(if (focused) E2.Select else E2.Panel.copy(alpha = 0f))
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${nc.number}",
                                        color = if (playing) E2.Gold else if (focused) E2.Text else E2.Soft,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Spacer(Modifier.width(18.dp))
                                    Text(
                                        nc.channel.name,
                                        color = if (playing && !focused) E2.Gold else E2.Text,
                                        fontSize = 20.sp,
                                        fontWeight = if (focused || playing) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (nc.favorite) Text("★", color = E2.Gold, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Tastenleiste unten (wie die Farbtasten-Leiste bei Enigma2)
            Row(
                modifier = Modifier.fillMaxWidth().background(E2.Bar).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (showBouquets) {
                    KeyHint("OK", "Bouquet öffnen")
                    KeyHint("◀ ▶", "Seite")
                    KeyHint("Zurück", "Schließen")
                } else {
                    KeyHint("OK", "Umschalten")
                    KeyHint("◀ ▶", "Seite")
                    KeyHint("OK lang", "Favorit")
                    KeyHint("Zurück", "Bouquets")
                }
            }
        }
        // Rechts bleibt das laufende Bild sichtbar; Tippen schließt die Liste.
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClose))
    }
}

@Composable
private fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            key,
            color = E2.Panel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.background(E2.Soft, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Text("  $label", color = E2.Text, fontSize = 14.sp)
    }
}

/**
 * Liste, die mit Links/Rechts seitenweise blättert und beim Öffnen auf einen Eintrag springt.
 * Die Menütaste ruft onMenu auf (z. B. Wechsel zur Bouquet-Übersicht).
 */
@Composable
private fun PagedList(
    count: Int,
    startIndex: Int,
    scope: CoroutineScope,
    onMenu: () -> Unit,
    row: @Composable (index: Int, requester: FocusRequester, onFocused: () -> Unit) -> Unit,
) {
    val state: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val requesters = remember(count) { List(count) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(startIndex) }

    fun focusIndex(target: Int) {
        val t = target.coerceIn(0, count - 1)
        scope.launch {
            state.scrollToItem((t - 3).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { requesters[t].requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters[startIndex.coerceIn(0, count - 1)].requestFocus() }
    }

    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val page = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                when (e.key) {
                    Key.DirectionRight, Key.PageDown, Key.ChannelDown -> {
                        focusIndex(focusedIndex + page); true
                    }
                    Key.DirectionLeft, Key.PageUp, Key.ChannelUp -> {
                        focusIndex(focusedIndex - page); true
                    }
                    Key.Menu -> {
                        onMenu(); true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(List(count) { it }) { index, _ ->
            row(index, requesters[index]) { focusedIndex = index }
        }
    }
}
