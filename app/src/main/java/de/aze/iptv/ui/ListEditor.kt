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
import androidx.compose.runtime.getValue
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
    BackHandler {
        when {
            movingGroup != null -> movingGroup = null
            movingChannel != null -> movingChannel = null
            else -> onClose()
        }
    }
    val firstGroup = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstGroup.requestFocus() }
    }

    val groups = app.editGroups
    val selected = groups.firstOrNull { it.key == selectedKey } ?: groups.firstOrNull()
    val hidden = app.hiddenChannels

    Column(Modifier.fillMaxSize().background(Palette.ScreenBrush).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Senderliste verwalten", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "OK = ein-/ausblenden · OK lang = verschieben (▲▼, OK legt ab) · Menü = umbenennen · ◀ ▶ wechselt zwischen Kategorien und Sendern",
                    color = Palette.TextMuted, fontSize = 12.sp,
                )
            }
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
                        ManageRow(
                            title = g.name,
                            subtitle = "${g.channels.size} Sender" + (g.sourceName?.let { " · $it" } ?: ""),
                            visible = !g.hidden,
                            moving = movingGroup == g.key,
                            active = selected?.key == g.key,
                            modifier = if (i == 0) Modifier.focusRequester(firstGroup) else Modifier,
                            onFocus = { if (movingGroup == null) selectedKey = g.key },
                            onToggle = { app.toggleGroupHidden(g.key) },
                            onMoveStart = { movingGroup = g.key },
                            onMove = { d -> app.moveGroup(g.key, d) },
                            onMoveEnd = { movingGroup = null },
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        itemsIndexed(g.channels, key = { _, c -> c.key }) { _, ch ->
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
                                onFocus = {},
                                onToggle = { app.toggleChannelHidden(ch.key) },
                                onMoveStart = { movingChannel = ch.key },
                                onMove = { d -> app.moveChannel(g.key, ch.key, d) },
                                onMoveEnd = { movingChannel = null },
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
