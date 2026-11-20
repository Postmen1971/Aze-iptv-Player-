package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.RemoteHelpGuest
import de.aze.iptv.RemoteHelpTime
import kotlinx.coroutines.delay

/**
 * Fernhilfe geben: Playlists und Senderliste der anderen Box – mit derselben Bedienung wie bei den eigenen.
 * Jede Änderung wird sofort an die andere Box geschickt; die Senderliste beim Schließen der Verwaltung.
 */
@Composable
fun RemoteHelpGuestScreen(guest: RemoteHelpGuest, onClose: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val model = guest.list
    if (editing && model != null) {
        LaunchedEffect(Unit) { guest.editing = true }
        ListEditor(
            model,
            onClose = { guest.sendList(model); guest.editing = false; editing = false },
            title = "Senderliste der anderen Box",
            note = "Fernhilfe – Änderungen werden beim Schließen („Fertig“) übertragen.",
        )
        return
    }
    BackHandler(onBack = onClose)
    val now by produceState(RemoteHelpTime.now()) { while (true) { value = RemoteHelpTime.now(); delay(1_000) } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Palette.ScreenBrush).verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Fernhilfe" + guest.device.takeIf { it.isNotBlank() }?.let { " für $it" }.orEmpty(),
            color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
        )
        val closed = guest.closed
        Text(
            closed ?: guest.status,
            color = if (closed != null) Palette.Live else if (guest.connected) Palette.Accent else Palette.TextSoft,
            fontSize = 15.sp,
        )
        if (guest.connected && closed == null) {
            val left = ((guest.expiresAt - now) / 1000).coerceAtLeast(0)
            Text(
                "Offen noch etwa ${left / 60}:${"%02d".format(left % 60)} min · jede Aktion verlängert um 1 Minute.",
                color = Palette.TextMuted, fontSize = 13.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (guest.connected && closed == null) {
                TvButton("Senderliste sortieren & ausblenden", modifier = Modifier.focusRequester(first), onClick = {
                    if (guest.list != null) editing = true
                })
                TvButton("Liste neu laden", onClick = { guest.refresh() })
            }
            TvButton(
                "Fernhilfe beenden", danger = true,
                modifier = if (guest.connected && closed == null) Modifier else Modifier.focusRequester(first),
                onClick = onClose,
            )
        }
        if (guest.connected && closed == null) {
            Text(
                "${guest.list?.editGroups?.size ?: 0} Gruppen in der Senderliste. Playlists unten: hinzufügen, ändern, " +
                    "pausieren oder löschen – das geschieht sofort auf der anderen Box.",
                color = Palette.TextMuted, fontSize = 13.sp,
            )
            PlaylistSection(guest, title = "Playlists der anderen Box")
        }
    }
}
