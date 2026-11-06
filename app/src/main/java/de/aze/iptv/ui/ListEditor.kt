package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController

/**
 * Senderlisten-Verwaltung im Stil von IBO Player:
 *  links die Kategorien, rechts die Sender der gewählten Kategorie.
 *  OK = ein-/ausblenden (Häkchen) · OK lang = Verschieben (dann ▲▼, OK legt ab) · Menü-Taste = umbenennen.
 *  Auf dem Tablet: tippen = ein-/ausblenden, lange drücken = verschieben (▲▼ erscheinen), „›“ zeigt die Sender.
 */
@Composable
fun ListEditor(app: AppController, onClose: () -> Unit) {
    var selectedKey by remember { mutableStateOf(app.editGroups.firstOrNull()?.key) }
    var movingGroup by remember { mutableStateOf<String?>(null) }
    var movingChannel by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    // Sortier-Modus: OK nimmt den Eintrag auf (▲▼ verschieben, OK legt ab) – ohne langes Drücken
    var sorting by remember { mutableStateOf(false) }
    // Beim Verschieben mitscrollen und den Fokus auf dem verschobenen Eintrag halten
    // (sonst rutschte er oben aus dem Bild, der Fokus sprang auf die Knöpfe und es wirkte wie ein Hänger)
    val groupState = androidx.compose.foundation.lazy.rememberLazyListState()
    val channelState = androidx.compose.foundation.lazy.rememberLazyListState()
    val moveScope = androidx.compose.runtime.rememberCoroutineScope()
    val movingReq = remember { FocusRequester() }
    // ▶ von den Kategorien direkt zum ersten Sender, ◀ zurück zur gewählten Kategorie
    val firstChannelReq = remember { FocusRequester() }
    val selectedGroupReq = remember { FocusRequester() }
    fun follow(state: androidx.compose.foundation.lazy.LazyListState, idx: Int) {
        if (idx < 0) return
        moveScope.launch {
            val info = state.layoutInfo.visibleItemsInfo
            val first = info.firstOrNull()?.index ?: 0
            val last = info.lastOrNull()?.index ?: 0
            val count = (last - first + 1).coerceAtLeast(1)
            when {
                idx <= first -> state.scrollToItem((idx - 1).coerceAtLeast(0))
                idx >= last -> state.scrollToItem((idx - count + 2).coerceAtLeast(0))
            }
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            kotlinx.coroutines.delay(30)
            runCatching { movingReq.requestFocus() }
        }
    }
    BackHandler {
        when {
            movingGroup != null -> movingGroup = null
            movingChannel != null -> movingChannel = null
            else -> onClose()
        }
    }
    val firstGroup = remember { FocusRequester() }
    // Fokus auf die erste Gruppe – auch wenn die Senderliste erst nach dem Öffnen fertig geladen ist
    val hasGroups = app.editGroups.isNotEmpty()
    LaunchedEffect(hasGroups) {
        if (!hasGroups) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstGroup.requestFocus() }
        qaLog("SORT_OPEN ${app.editGroups.size} Gruppen")
    }

    val groups = app.editGroups
    // andere Kategorie gewählt → Senderliste wieder von oben (dann ist der erste Sender sofort fokussierbar)
    LaunchedEffect(selectedKey) { runCatching { channelState.scrollToItem(0) } }
    val selected = groups.firstOrNull { it.key == selectedKey } ?: groups.firstOrNull()
    val hidden = app.hiddenChannels

    Column(Modifier.fillMaxSize().background(Palette.ScreenBrush).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Senderliste verwalten", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (sorting) "SORTIEREN: OK = Kategorie bzw. Sender aufnehmen · ▲▼ verschieben · OK = ablegen · ▶ zu den Sendern · fertig: „Sortieren beenden“"
                    else "OK = ein-/ausblenden · OK lang = verschieben (▲▼, OK legt ab) · Menü = umbenennen · ◀ ▶ wechselt zwischen Kategorien und Sendern",
                    color = if (sorting) Palette.Highlight else Palette.TextMuted, fontSize = 12.sp,
                )
            }
            TvButton(if (sorting) "Sortieren beenden" else "Sortieren", selected = sorting, onClick = {
                sorting = !sorting
                movingGroup = null; movingChannel = null
            })
            selected?.let { g ->
                TvButton("Umbenennen", onClick = { renaming = g.key })
                TvButton("Zurücksetzen", onClick = { app.resetGroup(g.key) })
            }
            TvButton("Fertig", onClick = onClose)
        }
        if (groups.isEmpty()) {
            Text("Noch keine Sender geladen.", color = Palette.TextMuted, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
            return@Column
        }
        Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---- Kategorien ----
            Column(Modifier.width(380.dp).fillMaxHeight().background(Palette.PanelBrush, RoundedCornerShape(14.dp)).padding(8.dp)) {
                Text("Kategorien (${groups.count { !it.hidden }}/${groups.size} sichtbar)", color = Palette.TextSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(6.dp))
                LazyColumn(state = groupState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
                        ManageRow(
                            title = g.name,
                            subtitle = "${g.channels.size} Sender" + (g.sourceName?.let { " · $it" } ?: ""),
                            visible = !g.hidden,
                            moving = movingGroup == g.key,
                            active = selected?.key == g.key,
                            modifier = (if (i == 0) Modifier.focusRequester(firstGroup) else Modifier)
                                .then(if (movingGroup == g.key) Modifier.focusRequester(movingReq) else Modifier)
                                .then(if (selected?.key == g.key && movingGroup == null) Modifier.focusRequester(selectedGroupReq) else Modifier)
                                // ▶ springt über die normale Fokus-Steuerung zum ersten Sender (kein eigenes requestFocus → kein Absturz)
                                .focusProperties {
                                    right = if (channelState.layoutInfo.visibleItemsInfo.any { it.index == 0 }) firstChannelReq else FocusRequester.Default
                                },
                            onFocus = { if (movingGroup == null) selectedKey = g.key; qaLog("SORT_G_FOCUS $i") },
                            onToggle = { if (sorting) movingGroup = g.key else app.toggleGroupHidden(g.key) },
                            onMoveStart = { movingGroup = g.key; qaLog("SORT_START ${g.name} $i") },
                            onMove = { d ->
                                app.moveGroup(g.key, d)
                                val idx = app.editGroups.indexOfFirst { it.key == g.key }
                                qaLog("SORT_MOVE ${g.name} $idx")
                                follow(groupState, idx)
                            },
                            onMoveEnd = { movingGroup = null; qaLog("SORT_END ${g.name} ${app.editGroups.indexOfFirst { it.key == g.key }}") },
                            onOpen = { selectedKey = g.key },
                            onRename = { renaming = g.key },
                        )
                    }
                }
            }
            // ---- Sender der gewählten Kategorie ----
            Column(Modifier.weight(1f).fillMaxHeight().background(Palette.PanelBrush, RoundedCornerShape(14.dp)).padding(8.dp)) {
                val g = selected
                if (g != null) {
                    Text(
                        "${g.name} – ${g.channels.count { it.key !in hidden }}/${g.channels.size} Sender sichtbar" + if (g.hidden) " (Kategorie ausgeblendet)" else "",
                        color = Palette.TextSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(6.dp),
                    )
                    LazyColumn(state = channelState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        itemsIndexed(g.channels, key = { _, c -> c.key }) { ci, ch ->
                            val nr = app.lineup.byKey[ch.key]?.number
                            ManageRow(
                                title = ch.name,
                                subtitle = when {
                                    ch.key in hidden -> "ausgeblendet"
                                    nr != null && app.isFavorite(ch) -> "Nr. $nr · Favorit"
                                    nr != null -> "Nr. $nr"
                                    else -> ""
                                },
                                visible = ch.key !in hidden,
                                moving = movingChannel == ch.key,
                                active = false,
                                modifier = (if (movingChannel == ch.key) Modifier.focusRequester(movingReq) else Modifier)
                                    .then(if (ci == 0) Modifier.focusRequester(firstChannelReq) else Modifier)
                                    .focusProperties {
                                        val si = app.editGroups.indexOfFirst { it.key == selectedKey }
                                        left = if (groupState.layoutInfo.visibleItemsInfo.any { it.index == si }) selectedGroupReq else FocusRequester.Default
                                    },
                                onFocus = { qaLog("SORT_CH_FOCUS $ci") },
                                onToggle = { if (sorting) movingChannel = ch.key else app.toggleChannelHidden(ch.key) },
                                onMoveStart = { movingChannel = ch.key; qaLog("SORT_CH_START ${ch.name} $ci") },
                                onMove = { d ->
                                    app.moveChannel(g.key, ch.key, d)
                                    follow(channelState, app.editGroups.firstOrNull { it.key == g.key }?.channels?.indexOfFirst { it.key == ch.key } ?: -1)
                                },
                                onMoveEnd = {
                                    movingChannel = null
                                    qaLog("SORT_CH_END ${ch.name} ${app.editGroups.firstOrNull { it.key == g.key }?.channels?.indexOfFirst { it.key == ch.key }}")
                                },
                                onOpen = null,
                                onRename = null,
                            )
                        }
                    }
                }
            }
        }
    }

    renaming?.let { key ->
        val g = app.editGroups.firstOrNull { it.key == key }
        if (g != null) {
            InputDialog(
                label = "Neuer Name für „${g.originalName}“ (leer = Originalname)",
                initial = g.name,
                password = false,
                keyboard = androidx.compose.ui.text.input.KeyboardType.Text,
                onDone = {
                    app.renameGroup(key, it)
                    renaming = null
                },
                onCancel = { renaming = null },
            )
        }
    }
}

/** Eine Zeile mit Häkchen; im Verschiebe-Modus bewegen ▲▼ den Eintrag. */
@Composable
private fun ManageRow(
    title: String,
    subtitle: String,
    visible: Boolean,
    moving: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onToggle: () -> Unit,
    onMoveStart: () -> Unit,
    onMove: (Int) -> Unit,
    onMoveEnd: () -> Unit,
    onOpen: (() -> Unit)?,
    onRename: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Focusable(
            modifier = Modifier
                .weight(1f)
                .then(modifier)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) {
                        // OK-Loslassen im Verschiebe-Modus nicht als „Ein-/Ausblenden“ werten
                        return@onPreviewKeyEvent moving && isOkKey(e.key)
                    }
                    if (!moving && e.key == Key.Menu && onRename != null) { onRename(); return@onPreviewKeyEvent true }
                    if (!moving) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.DirectionUp -> { onMove(-1); true }
                        e.key == Key.DirectionDown -> { onMove(+1); true }
                        isOkKey(e.key) || e.key == Key.Back -> { onMoveEnd(); true }
                        else -> true
                    }
                },
            onClick = { if (moving) onMoveEnd() else onToggle() },
            onLongClick = { if (!moving) onMoveStart() },
            onFocus = onFocus,
        ) { focused ->
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            moving -> Modifier.background(Palette.Highlight.copy(alpha = 0.35f), shape).border(2.dp, Palette.Highlight, shape)
                            focused -> Modifier.background(Palette.FocusBrush, shape)
                            active -> Modifier.background(Palette.Raised, shape)
                            else -> Modifier
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val fg = if (focused && !moving) Palette.OnAccent else if (visible) Palette.Text else Palette.TextMuted
                Text(
                    if (moving) "⇅" else if (visible) "☑" else "☐",
                    color = if (focused && !moving) Palette.OnAccent else if (visible) Palette.Highlight else Palette.TextMuted,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Column(Modifier.weight(1f)) {
                    Text(title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = if (focused && !moving) Palette.OnAccent else Palette.TextMuted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        // Tablet: im Verschiebe-Modus Pfeile zum Antippen, sonst „›“ für die Sender der Kategorie
        if (moving) {
            TvButton("▲", onClick = { onMove(-1) })
            TvButton("▼", onClick = { onMove(+1) })
            TvButton("✓", onClick = onMoveEnd)
        } else if (onOpen != null) {
            TvButton("›", onClick = onOpen)
        }
    }
}
