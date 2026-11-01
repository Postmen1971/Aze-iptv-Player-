package de.aze.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.aze.iptv.AppController

/** Ein Eintrag im Ausblenden-Dialog. */
class HideEntry(val key: String, val name: String, val detail: String, val hidden: Boolean)

/** Gruppen der Senderliste ein-/ausblenden (Live-TV). */
@Composable
fun HideGroupsDialog(app: AppController, onClose: () -> Unit) {
    val multi = app.activeSources.size > 1
    HideListDialog(
        title = "Gruppen ausblenden",
        hint = "Haken = Gruppe wird in der Senderliste nicht angezeigt",
        entries = app.editGroups.map { g ->
            HideEntry(g.key, g.name, "${g.channels.size} Sender" + if (multi && g.sourceName != null) " · ${g.sourceName}" else "", g.hidden)
        },
        onApply = { app.setHiddenGroups(it) },
        onClose = onClose,
    )
}

/**
 * Liste mit Haken zum Ausblenden (wie beim IBO Player): unten OK · Alle auswählen · Abbrechen.
 * Haken = wird NICHT angezeigt. Erst OK übernimmt die Auswahl. Für Live-TV-Gruppen und Film-/Serien-Kategorien.
 */
@Composable
fun HideListDialog(
    title: String,
    hint: String,
    entries: List<HideEntry>,
    onApply: (Set<String>) -> Unit,
    onClose: () -> Unit,
) {
    val all = entries
    var hidden by remember { mutableStateOf(all.filter { it.hidden }.map { it.key }.toSet()) }
    var warn by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .width(560.dp)
                .background(Palette.Panel, RoundedCornerShape(18.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (warn) "Mindestens ein Eintrag muss sichtbar bleiben." else hint,
                    color = if (warn) Palette.Live else Palette.TextMuted, fontSize = 13.sp,
                )
            }
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(all, key = { _, g -> g.key }) { i, g ->
                    val on = g.key in hidden
                    Focusable(
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        onClick = {
                            hidden = if (on) hidden - g.key else hidden + g.key
                            warn = false
                        },
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(
                                    if (focused) Palette.Highlight.copy(alpha = 0.16f) else Palette.Panel.copy(alpha = 0f),
                                    RoundedCornerShape(10.dp),
                                )
                                .then(if (focused) Modifier.border(1.5.dp, Palette.Highlight, RoundedCornerShape(10.dp)) else Modifier)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                g.name, color = if (on) Palette.TextMuted else Palette.Text, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                if (g.detail.isNotEmpty()) "  ${g.detail}" else "",
                                color = Palette.TextMuted, fontSize = 12.sp, maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            // Kästchen mit Haken
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(if (on) Palette.Highlight else Palette.Panel.copy(alpha = 0f), RoundedCornerShape(5.dp))
                                    .border(1.5.dp, if (on) Palette.Highlight else Palette.TextMuted, RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Text("✓", color = Palette.Ground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                TvButton("Alle auswählen", onClick = {
                    val keys = all.map { it.key }.toSet()
                    hidden = if (hidden.containsAll(keys)) emptySet() else keys
                    warn = false
                })
                TvButton("Abbrechen", onClick = onClose)
                TvButton("OK", selected = true, onClick = {
                    if (all.isNotEmpty() && all.all { it.key in hidden }) {
                        warn = true
                    } else {
                        onApply(hidden)
                        onClose()
                    }
                })
            }
        }
    }
}
