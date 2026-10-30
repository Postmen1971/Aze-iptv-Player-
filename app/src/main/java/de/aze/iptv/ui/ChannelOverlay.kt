package de.aze.iptv.ui

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
) {
    // Links in der Senderliste → Gruppen, nochmal Links → Menü (TV, Filme, Serien, Suchen, Einstellungen).
    // Zurück: Senderliste → Gruppen → App beenden.
    var showGroups by remember { mutableStateOf(startInGroups) }
    var showMenu by remember { mutableStateOf(false) }
    BackHandler {
        when {
            showMenu -> showMenu = false
            showGroups -> onExitApp()
            else -> showGroups = true
        }
    }

    val filter = app.playlistFilter
    val multi = app.sources.size > 1
    val groups = app.lineup.groups.filter {
        (it.channels.isNotEmpty() || it.isFavorites) && (it.isFavorites || filter == null || it.sourceId == filter)
    }
    if (groups.isEmpty()) return
    val currentKey = app.current?.channel?.key
    val startGroup = groups.indexOfFirst { g -> g.channels.any { it.channel.key == currentKey } }.coerceAtLeast(0)
    var groupIndex by remember { mutableIntStateOf(startGroup) }
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
                Text("Gruppen", color = Palette.GroupSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                val gState = rememberLazyListState(initialFirstVisibleItemIndex = (groupIndex - 5).coerceAtLeast(0))
                val gFirst = remember { FocusRequester() }
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
                            onClick = {
                                groupIndex = i
                                showGroups = false
                            },
                        ) { focused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(if (focused) Palette.GroupSelect else if (i == groupIndex) Palette.GroupActive else Palette.GroupBlue.copy(alpha = 0f), RoundedCornerShape(7.dp))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        (if (g.isFavorites) "★ " else "") + g.name,
                                        color = if (focused) Palette.OnAccent else Palette.Text,
                                        fontSize = 15.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(g.range, color = if (focused) Palette.OnAccent else Palette.GroupSoft, fontSize = 11.sp)
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
                modifier = Modifier.fillMaxWidth().focusRequester(headFocus),
                onClick = { switchGroup(+1) },
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
                            color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
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
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
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
                                    .then(if (index == start) Modifier.focusRequester(first) else Modifier),
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
                                        .height(if (prog != null) 40.dp else 34.dp)
                                        .background(if (focused) Palette.Accent else Palette.PanelDark.copy(alpha = 0f), RoundedCornerShape(7.dp))
                                        .then(if (playing && !focused) Modifier.border(1.5.dp, Palette.Highlight, RoundedCornerShape(7.dp)) else Modifier)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${nc.number}",
                                        color = if (focused) Palette.OnAccent else if (playing) Palette.Highlight else Palette.TextSoft,
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End, modifier = Modifier.width(38.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            nc.channel.name,
                                            color = if (focused) Palette.OnAccent else Palette.Text,
                                            fontSize = 15.sp,
                                            fontWeight = if (focused || playing) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        // Laufende Sendung (EPG) klein darunter, mit dünnem Fortschrittsbalken
                                        if (prog != null) {
                                            Text(
                                                prog.title,
                                                color = if (focused) Palette.OnAccent else Palette.TextMuted,
                                                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        runCatching { first.requestFocus() }
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
    val entries = buildList {
        add("TV" to onTv)
        add("Filme" to { onVod(false) })
        add("Serien" to { onVod(true) })
        add("Suchen" to onSearch)
        if (onFootball != null) add("Fußball heute" to onFootball)
        if (multi) add("Playlist: $playlistLabel" to onPlaylist)
        add("Einstellungen" to onSettings)
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(170.dp)
            .background(Palette.overlayBrush(0.96f))
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onRight(); true } else false
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun entry(label: String, action: () -> Unit, isFirst: Boolean = false, danger: Boolean = false) {
            Focusable(
                modifier = Modifier.fillMaxWidth().then(if (isFirst) Modifier.focusRequester(first) else Modifier),
                onClick = action,
            ) { focused ->
                Text(
                    label,
                    color = if (focused) Palette.OnAccent else if (danger) Palette.Live else Palette.Text,
                    fontSize = 16.sp,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focused) Modifier.background(Palette.FocusBrush, RoundedCornerShape(10.dp))
                            else Modifier,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        entries.forEachIndexed { i, (label, action) ->
            // Eine Zeile Abstand vor „Einstellungen“
            if (label == "Einstellungen") Spacer(Modifier.height(18.dp))
            entry(label, action, isFirst = i == 0)
        }
        // „App beenden“ ganz unten, gut sichtbar
        if (onQuit != null) {
            Spacer(Modifier.weight(1f))
            entry("App beenden", onQuit, danger = true)
        }
    }
}
