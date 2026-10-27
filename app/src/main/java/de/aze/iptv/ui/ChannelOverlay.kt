package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aze.iptv.AppController

/**
 * Senderliste über dem laufenden Bild (Entwurf E):
 * Gruppen links, Sender der Gruppe in der Mitte, rechts bleibt das Programm sichtbar.
 * OK = abspielen, OK lang = Favorit an/aus.
 */
@Composable
fun ChannelOverlay(app: AppController, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    BackHandler(onBack = onClose)

    val lineup = app.lineup
    val groups = lineup.groups.filter { it.channels.isNotEmpty() || it.isFavorites }
    val currentKey = app.current?.channel?.key
    val startGroup = groups.indexOfFirst { g -> g.channels.any { it.channel.key == currentKey } }.coerceAtLeast(0)
    var selectedGroup by remember { mutableIntStateOf(startGroup) }
    var firstOpen by remember { mutableIntStateOf(1) }
    val groupFocus = remember { FocusRequester() }

    // Falls die Gruppe keine Sender hat (z. B. noch keine Favoriten): Fokus auf die Gruppe setzen.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        if (firstOpen == 1) {
            runCatching { groupFocus.requestFocus() }
            firstOpen = 0
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Gruppen
        Column(
            modifier = Modifier
                .width(290.dp)
                .fillMaxHeight()
                .background(Palette.PanelDark.copy(alpha = 0.96f))
                .padding(horizontal = 12.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("GRUPPEN", color = Palette.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TvButton("Einstellungen", onClick = onOpenSettings)
            }
            val groupState = rememberLazyListState(initialFirstVisibleItemIndex = startGroup)
            LazyColumn(state = groupState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(groups) { index, group ->
                    Focusable(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == startGroup) Modifier.focusRequester(groupFocus) else Modifier),
                        onClick = { selectedGroup = index },
                        onFocus = { selectedGroup = index },
                    ) { focused ->
                        val active = index == selectedGroup
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(
                                    when {
                                        focused -> Palette.Accent
                                        active -> Palette.Raised
                                        else -> Palette.PanelDark.copy(alpha = 0f)
                                    },
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val fg = if (focused) Palette.OnAccent else Palette.Text
                            Text(
                                if (group.isFavorites) "★ ${group.name}" else group.name,
                                color = fg,
                                fontSize = 18.sp,
                                fontWeight = if (focused || active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(group.range, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Sender der gewählten Gruppe
        val group = groups.getOrNull(selectedGroup)
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxHeight()
                .background(Palette.Panel.copy(alpha = 0.96f))
                .padding(horizontal = 12.dp, vertical = 18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                Text(group?.name ?: "", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${group?.channels?.size ?: 0} Sender", color = Palette.TextMuted, fontSize = 15.sp)
            }
            if (group == null || group.channels.isEmpty()) {
                Text(
                    if (group?.isFavorites == true) "Noch keine Favoriten. In einer Gruppe OK lang drücken, um einen Sender hinzuzufügen."
                    else "Keine Sender",
                    color = Palette.TextMuted,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                key(selectedGroup) {
                    val startIndex = if (firstOpen == 1) group.channels.indexOfFirst { it.channel.key == currentKey }.coerceAtLeast(0) else 0
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
                    val firstItem = remember { FocusRequester() }
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(group.channels, key = { _, nc -> nc.channel.key }) { index, nc ->
                            val playing = nc.channel.key == currentKey
                            Focusable(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == startIndex) Modifier.focusRequester(firstItem) else Modifier),
                                onClick = {
                                    app.play(nc)
                                    onClose()
                                },
                                onLongClick = { app.toggleFavorite(nc.channel) },
                            ) { focused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .background(
                                            if (focused) Palette.Accent else Palette.Panel.copy(alpha = 0f),
                                            RoundedCornerShape(10.dp),
                                        )
                                        .then(
                                            if (playing && !focused) Modifier.border(2.dp, Palette.Accent, RoundedCornerShape(10.dp)) else Modifier,
                                        )
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val fg = if (focused) Palette.OnAccent else Palette.Text
                                    Text(
                                        "${nc.number}",
                                        color = if (focused) Palette.OnAccent else if (playing) Palette.Accent else Palette.TextSoft,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(52.dp),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        nc.channel.name,
                                        color = fg,
                                        fontSize = 19.sp,
                                        fontWeight = if (focused || playing) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (nc.favorite) {
                                        Text("★", color = if (focused) Palette.OnAccent else Palette.Accent, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(Unit) {
                        if (firstOpen == 1) {
                            withFrameNanos { }
                            runCatching { firstItem.requestFocus() }
                            firstOpen = 0
                        }
                    }
                }
            }
        }

        // Rechts bleibt das Bild frei; ein Tipp darauf schließt die Liste (Touch).
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClose)) {
            if (app.loading) {
                Text(
                    "Senderliste wird aktualisiert …",
                    color = Palette.TextSoft,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .background(Palette.Ground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.size(1.dp))
        }
    }
}
