package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController
import de.aze.iptv.data.Football
import de.aze.iptv.data.MatchChannel
import de.aze.iptv.data.TodayMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private class GuideData(
    val matches: List<TodayMatch>,
    /** eigene Sender je Spiel (Programmzeitschrift + TV-Plan zugeordnet) */
    val channels: Map<String, List<MatchChannel>>,
    /** alle Sender laut TV-Plan je Spiel */
    val listed: Map<String, List<de.aze.iptv.data.TvListings.Broadcast>>,
    val error: String?,
    /** Woher kamen Daten – zur Fehlersuche unten angezeigt */
    val diag: String = "",
    /** zur Fehlersuche: ähnliche Spiele bei liveonsat je Spiel */
    val similar: Map<String, List<String>> = emptyMap(),
)

/**
 * „Fußball heute“: alle Spiele des Tages (wie in SkorText) – OK öffnet ein Fenster mit den Sendern,
 * die das Spiel laut Programmzeitschrift zeigen. OK auf einem Sender schaltet hin, Zurück schließt das Fenster.
 */
@Composable
fun FootballGuide(app: AppController, onClose: () -> Unit) {
    val epg = app.epg
    val lineup = app.lineup
    var popup by remember { mutableStateOf<TodayMatch?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    BackHandler { if (popup != null) popup = null else onClose() }

    val data by produceState<GuideData?>(null, epg, lineup, reload) {
        value = withContext(Dispatchers.IO) {
            val matches = runCatching { Football.today() }
            val list = matches.getOrDefault(emptyList())
            val plan = emptyList<de.aze.iptv.data.TvListings.Broadcast>()
            // Erste Quelle für Sender: liveonsat.com (wie das Enigma2-Plugin FootOnSat)
            val los = runCatching { de.aze.iptv.data.LiveOnSat.today() }.getOrDefault(emptyList())
            val names = Football.nameIndex(lineup)
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val idx = Football.index(epg, lineup, dayStart, dayStart + 30 * 3_600_000L)
            val listed = list.associate { m ->
                // Nur europäische Sender, deutsche/türkische zuerst
                val fromLos = de.aze.iptv.data.LiveOnSat.europeanSorted(de.aze.iptv.data.LiveOnSat.forMatch(m, los))
                    .map { de.aze.iptv.data.TvListings.Broadcast("", it, "") }
                m.id to (fromLos + de.aze.iptv.data.TvListings.forMatch(m, plan)).distinctBy { it.channel.lowercase() }
            }
            GuideData(
                matches = list,
                channels = list.associate { m ->
                    val fromEpg = Football.channelsFor(m, idx)
                    val fromPlan = listed[m.id].orEmpty().flatMap { b ->
                        Football.matchByName(b.channel, names).map { MatchChannel(it, null, true, b.channel) }
                    }
                    m.id to (fromPlan + fromEpg).distinctBy { it.channel.channel.key }
                        .sortedWith(compareBy({ !it.sure }, { it.channel.number }))
                },
                listed = listed,
                error = matches.exceptionOrNull()?.let { "Spiele konnten nicht geladen werden (Internet?)" },
                similar = list.associate { m ->
                    m.id to de.aze.iptv.data.LiveOnSat.similar(m, los).map { "${it.home} v ${it.away} (${it.channels.size} Sender)" }
                },
                diag = "Quellen – liveonsat: ${de.aze.iptv.data.LiveOnSat.lastStatus} · " +
                    "Programmzeitschrift: ${idx.items.size} deiner Sender mit Programm",
            )
        }
    }

    val first = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(0) }
    LaunchedEffect(data, popup) {
        if (data != null && popup == null) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    val time = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.62f)
                .background(Palette.Ground.copy(alpha = 0.97f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Fußball heute", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("OK zeigt die Sender · Zurück schließt", color = Palette.TextMuted, fontSize = 12.sp)
            if (epg.channelCount == 0) {
                Text(
                    "Hinweis: Noch keine Programmzeitschrift geladen – ohne sie können keine Sender gefunden werden.",
                    color = Palette.Highlight, fontSize = 12.sp,
                )
            }
            val d = data
            d?.diag?.takeIf { it.isNotEmpty() }?.let { Text(it, color = Palette.TextMuted, fontSize = 11.sp) }
            when {
                d == null -> Text("Lade Spiele …", color = Palette.TextMuted, fontSize = 15.sp)
                d.matches.isEmpty() -> {
                    Text(d.error ?: "Heute keine Spiele in den SkorText-Ligen.", color = Palette.TextSoft, fontSize = 15.sp)
                    TvButton(if (d.error != null) "Nochmal versuchen" else "Schließen", modifier = Modifier.focusRequester(first), onClick = {
                        if (d.error != null) reload++ else onClose()
                    })
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(d.matches, key = { _, m -> m.id + m.kickoff }) { i, m ->
                        val newLeague = i == 0 || d.matches[i - 1].league != m.league
                        if (newLeague) {
                            Text(
                                m.league, color = Palette.Highlight, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp, start = 4.dp),
                            )
                        }
                        val n = d.channels[m.id].orEmpty().size
                        val planned = d.listed[m.id].orEmpty().size
                        Focusable(
                            // Fokus-Anker auf der zuletzt gewählten Zeile – nach dem Schließen des Fensters geht es dort weiter
                            modifier = Modifier.fillMaxWidth().then(if (i == selected) Modifier.focusRequester(first) else Modifier),
                            onClick = { popup = m },
                            onFocus = { selected = i },
                        ) { focused ->
                            val fg = if (focused) Palette.OnAccent else Palette.Text
                            val soft = if (focused) Palette.OnAccent else Palette.TextMuted
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (focused) Palette.Accent else Palette.Panel, RoundedCornerShape(7.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    if (m.live) m.status else time.format(Date(m.kickoff)),
                                    color = if (m.live && !focused) Palette.Live else fg,
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp),
                                )
                                Text(
                                    "${m.home} – ${m.away}", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                m.score?.let { Text(it, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                                Text(
                                    when {
                                        n > 0 -> "$n Sender"
                                        planned > 0 -> "$planned im Plan"
                                        else -> "–"
                                    },
                                    color = if (n > 0 && !focused) Palette.Highlight else soft, fontSize = 12.sp,
                                    modifier = Modifier.width(76.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val p = popup
        if (p != null) {
            MatchPopup(
                match = p,
                channels = data?.channels?.get(p.id).orEmpty(),
                listed = data?.listed?.get(p.id).orEmpty(),
                similar = data?.similar?.get(p.id).orEmpty(),
                current = app.current?.channel?.key,
                onPlay = { nc ->
                    app.play(nc)
                    popup = null
                    onClose()
                },
                onClose = { popup = null },
            )
        }
    }
}

@Composable
private fun MatchPopup(
    match: TodayMatch,
    channels: List<MatchChannel>,
    listed: List<de.aze.iptv.data.TvListings.Broadcast>,
    similar: List<String>,
    current: String?,
    onPlay: (de.aze.iptv.data.NumberedChannel) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember(match.id) { FocusRequester() }
    LaunchedEffect(match.id) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val time = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(480.dp)
                .heightIn(max = 420.dp)
                .background(Palette.Panel, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${match.home} – ${match.away}", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(
                match.league + " · " + (if (match.live) "läuft (${match.status})" else time.format(Date(match.kickoff)) + " Uhr"),
                color = Palette.TextMuted, fontSize = 13.sp,
            )
            if (channels.isEmpty()) {
                Text(
                    if (listed.isEmpty()) "Kein Sender gefunden – weder in deiner Programmzeitschrift noch im TV-Plan."
                    else "Keiner dieser Sender ist in deiner Senderliste:",
                    color = Palette.TextSoft, fontSize = 14.sp,
                )
                if (listed.isEmpty() && similar.isNotEmpty()) {
                    Text("Bei liveonsat gefunden (Name passt nicht genau): " + similar.joinToString(" · "), color = Palette.TextMuted, fontSize = 12.sp)
                }
                if (listed.isNotEmpty()) {
                    Text(listed.take(40).joinToString(", ") { it.channel }, color = Palette.Text, fontSize = 13.sp)
                }
                TvButton("Schließen", modifier = Modifier.focusRequester(first), onClick = onClose)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(channels) { i, mc ->
                        Focusable(
                            modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                            onClick = { onPlay(mc.channel) },
                        ) { focused ->
                            val fg = if (focused) Palette.OnAccent else Palette.Text
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (focused) Palette.Accent else if (mc.channel.channel.key == current) Palette.Raised else Palette.PanelDark,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${mc.channel.number}", color = if (focused) fg else Palette.Highlight, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                                    Text(mc.channel.channel.name, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (!mc.sure) Text("vermutlich", color = if (focused) fg else Palette.TextMuted, fontSize = 11.sp)
                                }
                                val prog = mc.programme
                                Text(
                                    if (prog != null) time.format(Date(prog.start)) + "  " + prog.title else "laut liveonsat: ${mc.listed}",
                                    color = if (focused) fg else Palette.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // Weitere Sender laut TV-Plan, die nicht in deiner Liste sind
                    val own = channels.mapNotNull { it.listed?.lowercase() }.toSet()
                    val others = listed.filter { it.channel.lowercase() !in own }
                    if (others.isNotEmpty()) {
                        item {
                            Text(
                                "Laut liveonsat außerdem: " + others.take(40).joinToString(", ") { it.channel },
                                color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
