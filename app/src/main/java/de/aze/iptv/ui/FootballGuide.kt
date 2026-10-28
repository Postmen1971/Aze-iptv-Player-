package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import de.aze.iptv.data.FootballMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * „Fußball im TV“: welches Spiel läuft wann auf welchem Sender (aus der Programmzeitschrift der eigenen Sender).
 * OK auf einem Sender schaltet direkt hin.
 */
@Composable
fun FootballGuide(app: AppController, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val epg = app.epg
    val lineup = app.lineup
    val matches by produceState<List<FootballMatch>?>(null, epg, lineup) {
        value = withContext(Dispatchers.Default) { Football.find(epg, lineup) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(matches) {
        if (matches != null) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    val time = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
    val dayFmt = remember { SimpleDateFormat("EEE dd.MM.", Locale.GERMANY) }
    val today = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }
    val now = System.currentTimeMillis()

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.72f)
                .background(Palette.Ground.copy(alpha = 0.97f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Fußball im TV", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Aus der Programmzeitschrift deiner Sender · OK schaltet um · Zurück schließt",
                color = Palette.TextMuted, fontSize = 12.sp,
            )
            val list = matches
            when {
                list == null -> Text("Suche Spiele …", color = Palette.TextMuted, fontSize = 15.sp)
                epg.channelCount == 0 -> {
                    Text(
                        "Noch keine Programmzeitschrift geladen. In den Einstellungen unter „Programmzeitschrift“ laden.",
                        color = Palette.TextSoft, fontSize = 15.sp,
                    )
                    TvButton("Programm jetzt laden", modifier = Modifier.focusRequester(first), onClick = { app.refreshEpg() })
                }
                list.isEmpty() -> {
                    Text("Heute und morgen keine Fußballspiele in deinen Sendern gefunden.", color = Palette.TextSoft, fontSize = 15.sp)
                    TvButton("Schließen", modifier = Modifier.focusRequester(first), onClick = onClose)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(list) { i, m ->
                        val p = m.programme
                        val live = p.start <= now && p.stop > now
                        val cal = Calendar.getInstance().apply { timeInMillis = p.start }
                        val day = if (cal.get(Calendar.DAY_OF_YEAR) == today) "" else dayFmt.format(Date(p.start)) + " "
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Palette.Panel, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.width(86.dp)) {
                                if (live) {
                                    Text("LIVE", color = Palette.Live, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Box(Modifier.fillMaxWidth().height(3.dp).background(Palette.Line)) {
                                        Box(Modifier.fillMaxWidth(p.progress(now)).height(3.dp).background(Palette.Live))
                                    }
                                } else {
                                    Text(day + time.format(Date(p.start)), color = Palette.Highlight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(p.title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    m.channels.forEachIndexed { j, nc ->
                                        TvButton(
                                            "${nc.number} · ${nc.channel.name}",
                                            selected = app.current?.channel?.key == nc.channel.key,
                                            modifier = if (i == 0 && j == 0) Modifier.focusRequester(first) else Modifier,
                                            onClick = {
                                                app.play(nc)
                                                onClose()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
