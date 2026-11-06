package de.aze.iptv.ui

import androidx.compose.ui.focus.focusProperties
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController

/**
 * Schmale Senderliste am linken Rand – das Bild bleibt groß sichtbar.
 *  Oben: Gruppenname mit ◀ ▶, darunter die Sender der Gruppe.
 *  Hoch/Runter: Sender, Links: Gruppen, Rechts: Liste schließen (Programm läuft weiter), Spulen: Gruppe wechseln, OK: umschalten, OK lang: Favorit.
 *  Links in der Gruppenübersicht: Menü mit TV, Filme, Serien, Suchen, Einstellungen.
 */
@Composable
fun ChannelOverlay(
    app: AppController,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onVod: (series: Boolean) -> Unit = {},
    onExitApp: () -> Unit = onClose,
    onFootball: (() -> Unit)? = null,
    startInGroups: Boolean = false,
    // Zähler vom Player: ▲/▼ kam an, aber die Liste hatte keinen Fokus → Fokus in die Liste holen
    focusKick: Int = 0,
    onFocusInside: (Boolean) -> Unit = {},
) {
    // Links in der Senderliste → Gruppen, nochmal Links → Menü (TV, Filme, Serien, Suchen, Einstellungen).
    // Zurück: Senderliste → Gruppen → App beenden.
    var showGroups by remember { mutableStateOf(startInGroups) }
    var showMenu by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { qaLog("LIST_CLOSED") } }
    BackHandler {
        when {
            showMenu -> showMenu = false
            showGroups -> onExitApp()
            else -> showGroups = true
        }
    }

    val filter = app.playlistFilter
    val multi = app.activeSources.size > 1
    // nur neu rechnen, wenn sich Liste oder Filter ändern (nicht bei jedem Neuzeichnen – große Listen)
    val lineup = app.lineup
    val groups = remember(lineup, filter) {
        lineup.groups.filter {
            (it.channels.isNotEmpty() || it.isFavorites) && (it.isFavorites || filter == null || it.sourceId == filter)
        }
    }
    if (groups.isEmpty()) return
    val currentKey = app.current?.channel?.key
    var groupIndex by remember {
        mutableIntStateOf(groups.indexOfFirst { g -> g.channels.any { it.channel.key == currentKey } }.coerceAtLeast(0))
    }
    var firstOpen by remember { mutableIntStateOf(1) }
    val headFocus = remember { FocusRequester() }

    fun switchGroup(delta: Int) {
        var g = groupIndex
        for (t in groups.indices) {
            g = Math.floorMod(g + delta, groups.size)
            if (groups[g].channels.isNotEmpty()) break
        }
        groupIndex = g
    }

    val group = groups[groupIndex.coerceIn(0, groups.lastIndex)]

    // Tablet/Handy: nach rechts wischen = eine Ebene weiter (Sender → Gruppen → Menü), nach links = zurück
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { onFocusInside(it.hasFocus) }
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onHorizontalDrag = { change, amount -> dx += amount; change.consume() },
                    onDragEnd = {
                        val limit = 60.dp.toPx()
                        when {
                            dx > limit -> if (!showGroups) showGroups = true else if (!showMenu) showMenu = true
                            dx < -limit -> when {
                                showMenu -> showMenu = false
                                showGroups -> showGroups = false
                                else -> onClose()
                            }
                        }
                    },
                )
            },
    ) {
        if (showMenu) {
            SideMenu(
                multi = multi,
                playlistLabel = app.playlistName(filter),
                onTv = { showMenu = false },
                onVod = onVod,
                onSearch = onSearch,
                onSettings = onOpenSettings,
                onPlaylist = {
                    app.nextPlaylistFilter()
                    groupIndex = 0
                },
                onRight = { showMenu = false },
                onFootball = onFootball,
                onQuit = onExitApp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 240.dp)
                .fillMaxWidth(0.27f)
                // Gruppenübersicht blau (wie Enigma2), Senderliste dunkel
                .background(if (showGroups) Palette.GroupBrush else Palette.overlayBrush(0.9f))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showGroups) {
                SectionLabel("Gruppen", modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 10.dp))
                val gState = rememberLazyListState(initialFirstVisibleItemIndex = (groupIndex - 5).coerceAtLeast(0))
                val gFirst = remember { FocusRequester() }
                LaunchedEffect(focusKick) { if (focusKick > 0) { withFrameNanos { }; runCatching { gFirst.requestFocus() } } }
                LazyColumn(
                    state = gState,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { showMenu = true; true } else false
                    },
                ) {
                    itemsIndexed(groups) { i, g ->
                        Focusable(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (i == groupIndex) Modifier.focusRequester(gFirst) else Modifier)
                                .onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                                        groupIndex = i; showGroups = false; true
                                    } else false
                                },
                            qaName = "G " + g.name,
                            onClick = {
                                groupIndex = i
                                showGroups = false
                            },
                        ) { focused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .background(
                                        if (focused) Palette.GroupSelect else if (i == groupIndex) Palette.Highlight.copy(alpha = 0.16f) else Palette.GroupBlue.copy(alpha = 0f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        (if (g.isFavorites) "★ " else "") + g.name,
                                        color = if (focused) Palette.OnAccent else Palette.TextSoft,
                                        fontSize = 15.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(g.range, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                // Fokus auf die Gruppen – auch wenn man aus dem Menü (Rechts) zurückkommt
                LaunchedEffect(showMenu) {
                    if (!showMenu) {
                        withFrameNanos { }
                        runCatching { gFirst.requestFocus() }
                    }
                    firstOpen = 0
                }
            } else {
            // Gruppenkopf: ◀ Name ▶
            Focusable(
                // Mit der Fernbedienung nur erreichbar, wenn die Gruppe leer ist (sonst landete ▲ hier und OK wechselte die Gruppe)
                modifier = Modifier.fillMaxWidth().focusRequester(headFocus)
                    .focusProperties { canFocus = group.channels.isEmpty() },
                onClick = { switchGroup(+1) },
                onFocus = { qaLog("LIST_HEAD_FOCUS") },
            ) { focused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(if (focused) Palette.Raised else Palette.PanelDark.copy(alpha = 0f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("◀", color = Palette.TextMuted, fontSize = 12.sp)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            (if (group.isFavorites) "★ " else "") + group.name,
                            color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (multi && filter == null && group.sourceName != null) {
                            Text(group.sourceName, color = Palette.TextMuted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    Text("▶", color = Palette.TextMuted, fontSize = 12.sp)
                }
            }
            // Punkte: wie viele Gruppen, welche ist offen
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                groups.forEachIndexed { i, _ ->
                    if (i < 20) Box(Modifier.size(5.dp).background(if (i == groupIndex) Palette.Accent else Palette.Line, CircleShape))
                }
            }

            if (group.channels.isEmpty()) {
                Text(
                    "Noch keine Favoriten. In einer Gruppe OK lang drücken.",
                    color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp),
                )
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    runCatching { headFocus.requestFocus() }
                    firstOpen = 0
                }
            } else {
                key(groupIndex) {
                    val start = if (firstOpen == 1) group.channels.indexOfFirst { it.channel.key == currentKey }.coerceAtLeast(0) else 0
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 5).coerceAtLeast(0))
                    val first = remember { FocusRequester() }
                    // Rundlauf: ▲ beim ersten Sender → letzter, ▼ beim letzten → erster (nie in die Gruppen-Kopfzeile)
                    val wrapScope = androidx.compose.runtime.rememberCoroutineScope()
                    val wrapReq = remember { FocusRequester() }
                    var focusedIdx by remember { mutableIntStateOf(start) }
                    var wrapTo by remember { mutableStateOf<Int?>(null) }
                    val lastIdx = group.channels.size - 1
                    fun wrap(target: Int) {
                        qaLog("LIST_WRAP $target")
                        wrapTo = target
                        wrapScope.launch {
                            listState.scrollToItem((target - 5).coerceAtLeast(0))
                            withFrameNanos { }
                            withFrameNanos { }
                            runCatching { wrapReq.requestFocus() }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier
                            .onFocusChanged { qaLog("LIST_FOCUS has=${it.hasFocus}") }
                            .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                // Messpunkt für den Emulator-Test (welcher Sender hat den Fokus, wenn ▲/▼ ankommt)
                                Key.DirectionUp, Key.DirectionDown -> { qaLog("LIST_KEY ${if (e.key == Key.DirectionUp) "up" else "down"} idx=$focusedIdx/$lastIdx"); false }
                                Key.DirectionLeft -> { showGroups = true; true }
                                // Rechts in der Senderliste: Liste schließen, das laufende Programm läuft weiter
                                Key.DirectionRight -> { onClose(); true }
                                Key.MediaFastForward -> { switchGroup(+1); true }
                                Key.MediaRewind -> { switchGroup(-1); true }
                                else -> false
                            }
                        },
                    ) {
                        itemsIndexed(group.channels, key = { _, nc -> nc.channel.key }) { index, nc ->
                            val playing = nc.channel.key == currentKey
                            Focusable(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == start) Modifier.focusRequester(first) else Modifier)
                                    .then(if (index == wrapTo) Modifier.focusRequester(wrapReq) else Modifier)
                                    // Rundlauf direkt am Sender: ▲ beim ersten → letzter, ▼ beim letzten → erster
                                    .onPreviewKeyEvent { e ->
                                        if (e.type != KeyEventType.KeyDown || lastIdx <= 0) return@onPreviewKeyEvent false
                                        when {
                                            e.key == Key.DirectionUp && index == 0 -> { wrap(lastIdx); true }
                                            e.key == Key.DirectionDown && index == lastIdx -> { wrap(0); true }
                                            else -> false
                                        }
                                    },
                                onFocus = { focusedIdx = index; qaLog("LIST_ITEM_FOCUS $index") },
                                qaName = "C " + nc.channel.name,
                                onClick = {
                                    app.play(nc)
                                    onClose()
                                },
                                onLongClick = { app.toggleFavorite(nc.channel) },
                            ) { focused ->
                                val prog = app.epg.now(nc.channel)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (prog != null) 44.dp else 38.dp)
                                        .background(
                                            if (focused) Palette.Accent else if (playing) Palette.Highlight.copy(alpha = 0.16f) else Palette.PanelDark.copy(alpha = 0f),
                                            RoundedCornerShape(10.dp),
                                        )
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${nc.number}",
                                        color = if (focused) Palette.OnAccent else if (playing) Palette.Highlight else Palette.TextMuted,
                                        fontSize = 13.sp, fontWeight = FontWeight.Normal,
                                        textAlign = TextAlign.End, modifier = Modifier.width(38.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            nc.channel.name,
                                            color = if (focused) Palette.OnAccent else Palette.TextSoft,
                                            fontSize = 15.sp,
                                            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        // Laufende Sendung (EPG) klein darunter, mit dünnem Fortschrittsbalken
                                        if (prog != null) {
                                            Text(
                                                prog.title,
                                                color = if (focused) Palette.OnAccent else Palette.EpgText,
                                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Box(Modifier.fillMaxWidth().height(2.dp).background(if (focused) Palette.OnAccent.copy(alpha = 0.25f) else Palette.Line)) {
                                                Box(
                                                    Modifier.fillMaxWidth(prog.progress(System.currentTimeMillis())).height(2.dp)
                                                        .background(if (focused) Palette.OnAccent else Palette.Highlight),
                                                )
                                            }
                                        }
                                    }
                                    if (nc.favorite) Text("★", color = if (focused) Palette.OnAccent else Palette.Highlight, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    LaunchedEffect(focusKick) {
                        if (focusKick > 0) { withFrameNanos { }; runCatching { first.requestFocus() }; qaLog("LIST_KICK $focusKick"); return@LaunchedEffect }
                    }
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        val ok = runCatching { first.requestFocus() }.isSuccess
                        qaLog("LIST_OPEN ${group.name} start=$start von ${group.channels.size} fokus=$ok")
                        firstOpen = 0
                    }
                }
            }
            }
        }
        // Rest bleibt Bild; Tippen schließt die Liste.
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClose)) {
            if (app.loading) {
                Text(
                    "Senderliste wird aktualisiert …",
                    color = Palette.TextSoft, fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .background(Palette.Ground.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Focusable(onClick = onClick) { focused ->
        Text(
            label,
            color = if (focused) Palette.OnAccent else Palette.TextSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .background(if (focused) Palette.Accent else Palette.Raised, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Schmales Menü links: TV, Filme, Serien, Suchen, Einstellungen. */
@Composable
internal fun SideMenu(
    multi: Boolean,
    playlistLabel: String,
    onTv: () -> Unit,
    onVod: (series: Boolean) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onPlaylist: () -> Unit,
    onRight: () -> Unit,
    onFootball: (() -> Unit)? = null,
    onQuit: (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    // Einträge mit dünnen Linien-Symbolen (ruhiges Design „Orbit“)
    val entries = buildList {
        add(Triple("TV", LineIcon.TV, onTv))
        add(Triple("Filme", LineIcon.MOVIE, { onVod(false) }))
        add(Triple("Serien", LineIcon.SERIES, { onVod(true) }))
        add(Triple("Suchen", LineIcon.SEARCH, onSearch))
        if (onFootball != null) add(Triple("Fußball heute", LineIcon.FOOTBALL, onFootball))
        if (multi) add(Triple("Playlist: $playlistLabel", LineIcon.PLAYLIST, onPlaylist))
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(236.dp)
            .background(Palette.Ground.copy(alpha = 0.96f))
            .padding(start = 18.dp, end = 14.dp, top = 26.dp, bottom = 18.dp)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onRight(); true } else false
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Logo oben
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 10.dp, bottom = 26.dp),
        ) {
            AzeLogo(28.dp)
            // unter dem Schriftzug: Uhrzeit mit Datum
            Column {
                AzeWordmark(18.sp)
                AzeClock(13.sp)
            }
        }

        @Composable
        fun entry(label: String, icon: LineIcon, action: () -> Unit, isFirst: Boolean = false, danger: Boolean = false) {
            Focusable(
                modifier = Modifier.fillMaxWidth().then(if (isFirst) Modifier.focusRequester(first) else Modifier),
                onClick = action,
                qaName = "M " + label,
            ) { focused ->
                val fg = if (focused) Palette.OnAccent else if (danger) Palette.Live.copy(alpha = 0.85f) else Palette.TextSoft
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .then(if (focused) Modifier.background(Palette.Accent, RoundedCornerShape(12.dp)) else Modifier)
                        .padding(horizontal = 12.dp),
                ) {
                    LineIconView(icon, fg, 20.dp)
                    Text(
                        label, color = fg, fontSize = 15.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        entries.forEachIndexed { i, (label, icon, action) -> entry(label, icon, action, isFirst = i == 0) }
        Spacer(Modifier.weight(1f))
        entry("Einstellungen", LineIcon.SETTINGS, onSettings)
        // „App beenden“ ganz unten
        if (onQuit != null) entry("App beenden", LineIcon.EXIT, onQuit, danger = true)
    }
}
