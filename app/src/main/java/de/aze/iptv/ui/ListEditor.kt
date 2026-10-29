package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController

/**
 * Senderliste bearbeiten (wie die Bouquet-Bearbeitung bei Enigma2):
 *  Gruppen: verschieben, umbenennen, ein-/ausblenden, Sender öffnen
 *  Sender: innerhalb der Gruppe verschieben, ein-/ausblenden
 */
@Composable
fun ListEditor(app: AppController, onClose: () -> Unit) {
    var openGroup by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    BackHandler { if (openGroup != null) openGroup = null else onClose() }

    val back = remember { FocusRequester() }
    LaunchedEffect(openGroup) { runCatching { back.requestFocus() } }

    Box(modifier = Modifier.fillMaxSize().background(Palette.ScreenBrush), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val group = openGroup?.let { k -> app.editGroups.firstOrNull { it.key == k } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (group == null) "Senderliste bearbeiten" else group.name,
                        color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (group == null) "Gruppen verschieben, umbenennen oder ausblenden. „Sender“ öffnet die Gruppe."
                        else "Sender verschieben oder ausblenden. Ausgeblendete Sender bekommen keine Nummer.",
                        color = Palette.TextMuted, fontSize = 15.sp,
                    )
                }
                TvButton(
                    if (group == null) "Fertig" else "Zurück zu den Gruppen",
                    modifier = Modifier.focusRequester(back),
                    onClick = { if (group == null) onClose() else openGroup = null },
                )
            }

            if (group == null) {
                val groups = app.editGroups
                if (groups.isEmpty()) {
                    Text("Noch keine Sender geladen.", color = Palette.TextMuted, fontSize = 16.sp)
                }
                val multi = app.sources.size > 1
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
                        EditRow(
                            title = g.name,
                            subtitle = buildString {
                                append("${g.channels.size} Sender")
                                if (g.name != g.originalName) append(" · früher „${g.originalName}“")
                                if (multi && g.sourceName != null) append(" · ${g.sourceName}")
                            },
                            hidden = g.hidden,
                        ) {
                            TvButton("▲", onClick = { app.moveGroup(g.key, -1) })
                            TvButton("▼", onClick = { app.moveGroup(g.key, +1) })
                            TvButton(if (g.hidden) "Einblenden" else "Ausblenden", danger = !g.hidden, onClick = { app.toggleGroupHidden(g.key) })
                            TvButton("Umbenennen", onClick = { renaming = g.key })
                            TvButton("Sender ›", onClick = { openGroup = g.key })
                        }
                        if (i == groups.lastIndex) Box(Modifier.padding(bottom = 24.dp))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(if (group.hidden) "Gruppe einblenden" else "Ganze Gruppe ausblenden", onClick = { app.toggleGroupHidden(group.key) })
                    TvButton("Gruppe zurücksetzen", onClick = { app.resetGroup(group.key) })
                }
                val hidden = app.hiddenChannels
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(group.channels, key = { _, c -> c.key }) { _, ch ->
                        val isHidden = ch.key in hidden
                        val nr = app.lineup.byKey[ch.key]?.number
                        EditRow(
                            title = ch.name,
                            subtitle = when {
                                isHidden -> "ausgeblendet"
                                nr != null && app.isFavorite(ch) -> "Nr. $nr · Favorit"
                                nr != null -> "Nr. $nr"
                                else -> ""
                            },
                            hidden = isHidden,
                        ) {
                            TvButton("▲", onClick = { app.moveChannel(group.key, ch.key, -1) })
                            TvButton("▼", onClick = { app.moveChannel(group.key, ch.key, +1) })
                            TvButton(if (isHidden) "Einblenden" else "Ausblenden", danger = !isHidden, onClick = { app.toggleChannelHidden(ch.key) })
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

@Composable
private fun EditRow(
    title: String,
    subtitle: String,
    hidden: Boolean,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (hidden) Palette.TextMuted else Palette.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = if (hidden) Palette.Live else Palette.TextMuted, fontSize = 13.sp, maxLines = 1)
            }
        }
        actions()
    }
}
