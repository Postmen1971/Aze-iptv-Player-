package de.aze.iptv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import de.aze.iptv.AppController
import de.aze.iptv.data.Episode
import de.aze.iptv.data.Http
import de.aze.iptv.data.SeriesDetails
import de.aze.iptv.data.VodCategory
import de.aze.iptv.data.VodDetails
import de.aze.iptv.data.VodItem
import de.aze.iptv.data.VodProvider
import de.aze.iptv.player.AspectMode
import de.aze.iptv.player.FpsProbe
import de.aze.iptv.player.FrameRate
import de.aze.iptv.player.rememberDisplayWatch
import de.aze.iptv.player.awaitChange
import de.aze.iptv.player.TrackHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class Playing(val url: String, val title: String, val resumeKey: String, val startMs: Long)

/**
 * Filme & Serien – bedient sich wie Live-TV:
 *  links die Kategorien (mit Anzahl), rechts die Poster (durchnummeriert);
 *  ◀ in den Kategorien öffnet das Menü (TV, Filme, Serien, Suchen, Einstellungen).
 */
@Composable
fun VodScreen(app: AppController, startWithSeries: Boolean, onExit: () -> Unit, onOpenSettings: () -> Unit = onExit) {
    val vodSources = app.vodSources
    var series by remember { mutableStateOf(startWithSeries) }
    var sourceIdx by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<VodItem?>(null) }
    var playing by remember { mutableStateOf<Playing?>(null) }

    if (vodSources.isEmpty()) {
        BackHandler(onBack = onExit)
        Box(Modifier.fillMaxSize().background(Palette.Ground).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Noch keine Playlist vorhanden.", color = Palette.TextSoft, fontSize = 18.sp)
                TvButton("Zurück zum Fernsehen", onClick = onExit)
            }
        }
        return
    }
    val src = vodSources[sourceIdx.coerceIn(0, vodSources.lastIndex)]
    val api = remember(src.id, src.url, src.user) { app.vodProvider(src) }

    val p = playing
    if (p != null) {
        VodPlayer(app, p, onExit = { pos, dur ->
            app.saveResume(p.resumeKey, pos, dur)
            playing = null
        })
        return
    }

    val d = detail
    if (d != null) {
        DetailView(
            app = app, api = api, item = d, series = series, sourceId = src.id,
            onPlay = { playing = it },
            onBack = { detail = null },
        )
        return
    }

    Browse(
        app = app, api = api, series = series, sourceId = src.id,
        sourceLabel = src.name,
        multi = vodSources.size > 1,
        onSwitch = { series = it },
        onNextSource = { sourceIdx = (sourceIdx + 1) % vodSources.size },
        onOpen = { detail = it },
        onExit = onExit,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun Browse(
    app: AppController,
    api: VodProvider,
    series: Boolean,
    sourceId: String,
    sourceLabel: String,
    multi: Boolean,
    onSwitch: (Boolean) -> Unit,
    onNextSource: () -> Unit,
    onOpen: (VodItem) -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var cats by remember(api, series) { mutableStateOf<List<VodCategory>?>(null) }
    var selected by remember(api, series) { mutableIntStateOf(0) }
    var items by remember(api, series) { mutableStateOf<List<VodItem>?>(null) }
    var error by remember(api, series) { mutableStateOf<String?>(null) }
    var counts by remember(api, series) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var menu by remember { mutableStateOf(false) }
    var askSearch by remember { mutableStateOf(false) }
    var query by remember(api, series) { mutableStateOf<String?>(null) }
    var results by remember(api, series) { mutableStateOf<List<VodItem>?>(null) }
    val catFocus = remember { FocusRequester() }
    val kind = if (series) "Serien" else "Filme"

    BackHandler {
        when {
            menu -> onExit()
            query != null -> query = null
            else -> menu = true
        }
    }

    LaunchedEffect(api, series) {
        cats = runCatching { if (series) api.seriesCategories() else api.movieCategories() }
            .onFailure { error = "Kategorien konnten nicht geladen werden." }
            .getOrDefault(emptyList())
        withFrameNanos { }
        runCatching { catFocus.requestFocus() }
    }
    // Anzahl je Kategorie im Hintergrund holen (große Listen brauchen ein paar Sekunden)
    LaunchedEffect(api, series) {
        counts = runCatching { api.counts(series) }.getOrDefault(emptyMap())
    }
    // Erwachsenen-Filter: solche Kategorien gar nicht erst zeigen
    val adultOff = app.adultFilter
    val catList = remember(cats, adultOff) {
        cats.orEmpty().let { l -> if (adultOff) l.filterNot { de.aze.iptv.data.AdultFilter.isAdult(it.name) } else l }
    }
    LaunchedEffect(api, series, selected, cats) {
        val c = catList.getOrNull(selected) ?: return@LaunchedEffect
        items = null
        delay(250) // erst laden, wenn man kurz auf einer Kategorie stehen bleibt
        items = runCatching { if (series) api.series(c.id) else api.movies(c.id) }
            .onFailure { error = "Inhalte konnten nicht geladen werden." }
            .getOrDefault(emptyList())
    }
    LaunchedEffect(query) {
        val q = query ?: return@LaunchedEffect
        results = null
        results = runCatching { api.all(series) }.getOrDefault(emptyList())
            .filter { it.name.contains(q, ignoreCase = true) }
    }
    LaunchedEffect(menu) {
        if (!menu) {
            withFrameNanos { }
            runCatching { catFocus.requestFocus() }
        }
    }

    val ctxTouch = LocalContext.current
    val touchDevice = remember(ctxTouch) { !de.aze.iptv.player.FrameRate.isTv(ctxTouch) }
    Column(Modifier.fillMaxSize().background(Palette.Ground)) {
    // Tablet/Handy: Leiste oben zum Wechseln (auf dem Fernseher über ◀ im Seitenmenü)
    if (touchDevice) {
        Row(
            Modifier.fillMaxWidth().background(Palette.PanelDark).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvButton("📺 Live-TV", onClick = onExit)
            TvButton("Filme", selected = !series, onClick = { query = null; onSwitch(false) })
            TvButton("Serien", selected = series, onClick = { query = null; onSwitch(true) })
            TvButton("🔍 Suchen", onClick = { askSearch = true })
            if (multi) TvButton("Playlist: $sourceLabel", onClick = { query = null; onNextSource() })
            TvButton("⚙ Einstellungen", onClick = onOpenSettings)
        }
    }
    Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(Modifier.fillMaxSize()) {
            // Menü wie beim Fernsehen: schiebt sich links neben die Kategorien
            if (menu) {
                SideMenu(
                    multi = multi,
                    playlistLabel = sourceLabel,
                    onTv = onExit,
                    onVod = { s -> menu = false; query = null; onSwitch(s) },
                    onSearch = { menu = false; askSearch = true },
                    onSettings = onOpenSettings,
                    onPlaylist = { menu = false; query = null; onNextSource() },
                    onRight = { menu = false },
                )
            }
            Column(
                Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    // wie die Gruppenübersicht beim Fernsehen
                    .background(if (menu) Palette.GroupBlue else Palette.PanelDark)
                    .padding(10.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                            menu = true
                            true
                        } else false
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                    Text(kind, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (multi) Text("  $sourceLabel", color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (cats == null) Text("Lade Kategorien …", color = Palette.TextMuted, fontSize = 13.sp)
                error?.let { Text(it, color = Palette.Live, fontSize = 13.sp) }
                if (cats != null && catList.isEmpty() && error == null) {
                    Text("Keine $kind in dieser Playlist.", color = Palette.TextMuted, fontSize = 13.sp)
                    api.sourceInfo().takeIf { it.isNotBlank() }?.let { Text("Quelle: $it", color = Palette.TextMuted, fontSize = 11.sp) }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    itemsIndexed(catList) { i, c ->
                        Focusable(
                            modifier = Modifier.fillMaxWidth().then(if (i == selected) Modifier.focusRequester(catFocus) else Modifier),
                            onClick = { selected = i; query = null },
                            // Nur beim Wechsel der Kategorie die Suche beenden (nicht wenn der Fokus nach der Suche zurückkommt)
                            onFocus = { if (i != selected) { selected = i; query = null } },
                        ) { focused ->
                            val count = counts[c.id] ?: items?.size?.takeIf { i == selected }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            focused -> Palette.Accent
                                            i == selected -> Palette.Raised
                                            else -> Color.Transparent
                                        },
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    c.name,
                                    color = if (focused) Palette.OnAccent else Palette.Text,
                                    fontSize = 14.sp,
                                    fontWeight = if (focused || i == selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (count != null) {
                                    Text(
                                        "$count",
                                        color = if (focused) Palette.OnAccent else Palette.TextMuted,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                val q = query
                val raw = if (q != null) results else items
                val list = remember(raw, adultOff) {
                    raw?.let { l -> if (adultOff) l.filterNot { de.aze.iptv.data.AdultFilter.isAdult(it.name) } else l }
                }
                val title = if (q != null) "Suche: „$q“" else catList.getOrNull(selected)?.name.orEmpty()
                Column {
                    // Oben: Kategorie bzw. Suche und rechts die Anzahl
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            color = Palette.TextSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (list != null) Text("${list.size} $kind", color = Palette.Highlight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    when {
                        list == null -> Text(if (q != null) "Suche …" else "Lade …", color = Palette.TextMuted, fontSize = 15.sp)
                        list.isEmpty() -> Text(if (q != null) "Nichts gefunden." else "Keine Einträge in dieser Kategorie.", color = Palette.TextMuted, fontSize = 15.sp)
                        else -> {
                          val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                          // Poster der nächsten Reihen schon vorladen, damit beim schnellen Blättern Bilder da sind
                          val ctx = LocalContext.current
                          LaunchedEffect(list, gridState) {
                              val requested = HashSet<String>()
                              androidx.compose.runtime.snapshotFlow { gridState.firstVisibleItemIndex }.collect { first ->
                                  list.drop(first).take(48).forEach { item ->
                                      val url = item.poster ?: return@forEach
                                      if (requested.add(url)) coil.Coil.imageLoader(ctx).enqueue(posterRequest(ctx, url))
                                  }
                              }
                          }
                          LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItemsIndexed(list, key = { _, it -> it.id }) { idx, item ->
                                PosterCard(item, number = idx + 1, resumed = !series && app.resumeOf("m:$sourceId:${item.id}") > 0) { onOpen(item) }
                            }
                          }
                        }
                    }
                }
            }
        }

        if (askSearch) {
            InputDialog("$kind suchen", query.orEmpty(), password = false, keyboard = KeyboardType.Text, onDone = {
                askSearch = false
                query = it.trim().ifEmpty { null }
            }, onCancel = { askSearch = false })
        }
    }
    }
}

/** Poster in fester, kleiner Größe laden: schneller und passt immer in den Zwischenspeicher. */
private fun posterRequest(context: android.content.Context, url: String): coil.request.ImageRequest =
    coil.request.ImageRequest.Builder(context)
        .data(url)
        .size(200, 300)
        .crossfade(false)
        .build()

@Composable
private fun PosterCard(item: VodItem, number: Int, resumed: Boolean, onClick: () -> Unit) {
    Focusable(onClick = onClick) { focused ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Raised)
                    .then(if (focused) Modifier.border(3.dp, Palette.Accent, RoundedCornerShape(8.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.name, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 4, modifier = Modifier.padding(8.dp))
                if (item.poster != null) {
                    AsyncImage(
                        model = posterRequest(LocalContext.current, item.poster),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Laufende Nummer oben links
                Text(
                    "$number",
                    color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
                        .background(Palette.Ground.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                item.rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { r ->
                    Text(
                        "★ ${"%.1f".format(r)}",
                        color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                            .background(Palette.Ground.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (resumed) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Palette.Accent))
                }
            }
            Text(
                item.name,
                color = if (focused) Palette.Accent else Palette.Text,
                fontSize = 11.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailView(
    app: AppController,
    api: VodProvider,
    item: VodItem,
    series: Boolean,
    sourceId: String,
    onPlay: (Playing) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var info by remember(item.id) { mutableStateOf<VodDetails?>(null) }
    var sd by remember(item.id) { mutableStateOf<SeriesDetails?>(null) }
    var season by remember(item.id) { mutableIntStateOf(-1) }
    var failed by remember(item.id) { mutableStateOf(false) }
    // IMDb-Bewertung (nur mit eigenem OMDb-Schlüssel in den Einstellungen)
    var imdb by remember(item.id) { mutableStateOf<de.aze.iptv.data.Omdb.Rating?>(null) }
    val playButton = remember { FocusRequester() }

    LaunchedEffect(item.id) {
        runCatching {
            if (series) {
                val s = api.seriesDetails(item.id)
                sd = s
                info = s.info
                season = s.seasons.keys.firstOrNull() ?: -1
            } else {
                info = api.movieDetails(item.id)
            }
        }.onFailure { failed = true }
        withFrameNanos { }
        runCatching { playButton.requestFocus() }
    }
    val i = info

    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        i?.backdrop?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Palette.Ground, Palette.Ground.copy(alpha = 0.92f), Palette.Ground.copy(alpha = 0.55f))),
            ),
        )
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.width(180.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Palette.Raised)) {
                if (item.poster != null) AsyncImage(model = item.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.name, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val key = app.omdbKey
                LaunchedEffect(item.id, key, i) {
                    if (key.isBlank() || i == null) return@LaunchedEffect
                    imdb = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { de.aze.iptv.data.Omdb.rating(key, item.name, i.originalName, i.year ?: item.year) }.getOrNull()
                    }
                }
                // Bewertungen gut sichtbar: IMDb (falls Schlüssel) und die des Anbieters (meist TMDB), jeweils von 10
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    imdb?.let { r ->
                        Text(
                            "IMDb ${r.imdb}/10" + (r.votes?.let { "  ($it Stimmen)" } ?: ""),
                            color = Color(0xFF111111), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFF5C518), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    (i?.rating ?: item.rating)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { r ->
                        Text(
                            "★ ${"%.1f".format(r)}/10",
                            color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Palette.Raised, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                val meta = listOfNotNull(
                    i?.year ?: item.year,
                    i?.duration,
                    i?.genre,
                ).joinToString("  ·  ")
                if (meta.isNotEmpty()) Text(meta, color = Palette.TextSoft, fontSize = 14.sp)
                when {
                    failed -> Text("Details konnten nicht geladen werden.", color = Palette.Live, fontSize = 14.sp)
                    i == null -> Text("Lade …", color = Palette.TextMuted, fontSize = 14.sp)
                }
                i?.plot?.let { Text(it, color = Palette.Text, fontSize = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                i?.director?.let { Text("Regie: $it", color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                i?.cast?.let { Text("Mit: $it", color = Palette.TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }

                if (!series) {
                    val key = "m:$sourceId:${item.id}"
                    val resume = app.resumeOf(key)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (resume > 0) {
                            TvButton("Weiterschauen ab ${fmtTime(resume)}", modifier = Modifier.focusRequester(playButton), onClick = {
                                onPlay(Playing(api.movieUrl(item), item.name, key, resume))
                            })
                            TvButton("Von vorn", onClick = { onPlay(Playing(api.movieUrl(item), item.name, key, 0)) })
                        } else {
                            TvButton("Abspielen", modifier = Modifier.focusRequester(playButton), onClick = {
                                onPlay(Playing(api.movieUrl(item), item.name, key, 0))
                            })
                        }
                        TvButton("Zurück", onClick = onBack)
                    }
                } else {
                    val s = sd
                    if (s != null && s.seasons.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.seasons.keys.toList()) { n ->
                                TvButton(
                                    "Staffel $n",
                                    selected = n == season,
                                    modifier = if (n == s.seasons.keys.first()) Modifier.focusRequester(playButton) else Modifier,
                                    onClick = { season = n },
                                )
                            }
                        }
                        val eps = s.seasons[season].orEmpty()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            items(eps, key = { it.id }) { e ->
                                EpisodeRow(app, e, sourceId) { start ->
                                    onPlay(Playing(api.episodeUrl(e), "${item.name} – S${e.season} E${e.number}", "e:$sourceId:${e.id}", start))
                                }
                            }
                        }
                    } else if (s != null) {
                        Text("Keine Folgen gefunden.", color = Palette.TextMuted, fontSize = 14.sp)
                        TvButton("Zurück", modifier = Modifier.focusRequester(playButton), onClick = onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(app: AppController, e: Episode, sourceId: String, onPlay: (Long) -> Unit) {
    val resume = app.resumeOf("e:$sourceId:${e.id}")
    Focusable(modifier = Modifier.fillMaxWidth(), onClick = { onPlay(resume) }) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Palette.Accent else Palette.Panel.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${e.number}", color = if (focused) Palette.OnAccent else Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = if (focused) Palette.OnAccent else Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                e.plot?.let { Text(it, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (resume > 0) Text("ab ${fmtTime(resume)}", color = if (focused) Palette.OnAccent else Palette.Accent, fontSize = 12.sp)
            e.duration?.let { Text(it, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 12.sp) }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}


/** Sprungweite beim Spulen: je länger die Taste gehalten wird, desto weiter. */
private fun seekStep(repeat: Int): Long = when {
    repeat < 4 -> 10_000L
    repeat < 12 -> 30_000L
    repeat < 24 -> 60_000L
    else -> 300_000L
}

/**
 * Film-/Folgen-Player:
 *  ◀ ▶ (oder ◀◀ ▶▶) spulen gezielt – erst 10 s, beim Halten 30 s, 1 min, 5 min; gesprungen wird nach kurzer Pause.
 *  OK = Pause/weiter und Bedienleiste zeigen · ▲ ▼ / Menü = Bedienleiste · 0–9 = an 0 %, 10 % … 90 % springen.
 *  In der Leiste: Zurück 10 s, Pause, Vor 30 s, Tonspur, Untertitel, Bildformat.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VodPlayer(app: AppController, p: Playing, onExit: (posMs: Long, durMs: Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var showUi by remember { mutableStateOf(true) }
    var uiTick by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Long?>(null) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var position by remember { mutableLongStateOf(p.startMs) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var aspect by remember { mutableStateOf(AspectMode.FIT) }
    var picker by remember { mutableStateOf<Int?>(null) } // C.TRACK_TYPE_AUDIO / C.TRACK_TYPE_TEXT
    var toast by remember { mutableStateOf<String?>(null) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    val player = remember {
        val http = DefaultHttpDataSource.Factory().setUserAgent(Http.USER_AGENT).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(!de.aze.iptv.player.FrameRate.isTv(context))
            .build()
            .apply { videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF }
    }
    // Bildwiederholrate an den Film anpassen (z. B. 24 Hz bei Kinofilmen)
    val activity = remember(context) { FrameRate.findActivity(context) }
    // Umschalten wie bei TiviMate: anhalten → Frequenz umstellen → warten → an gleicher Stelle weiter
    val displayWatch = rememberDisplayWatch()
    var switching by remember { mutableStateOf(false) }
    fun restartAt(pos: Long) {
        player.stop()
        player.prepare()
        player.seekTo(pos)
        player.playWhenReady = true
    }
    val probe = remember(player) {
        FpsProbe { fps ->
            val act = activity ?: return@FpsProbe
            if (!app.afr || switching) return@FpsProbe
            val mode = FrameRate.target(act, fps) ?: return@FpsProbe
            switching = true
            val pos = player.currentPosition
            player.stop()
            scope.launch {
                val before = displayWatch.tick
                FrameRate.apply(act, mode)
                displayWatch.awaitChange(before, 6_000)
                delay(app.afrDelay * 1_000L)
                switching = false
                restartAt(pos)
            }
        }.also { it.attach(player) }
    }
    // Wächter: Film läuft, aber 5 s lang kein neues Bild → an gleicher Stelle neu starten (höchstens 3×)
    LaunchedEffect(player) {
        var last = -1L
        var still = 0
        var restarts = 0
        while (true) {
            delay(1_000)
            val f = probe.frames
            val active = !switching && player.playbackState == Player.STATE_READY && player.isPlaying && player.videoFormat != null
            if (active && f == last) still++ else still = 0
            last = f
            if (still >= 5 && restarts < 3) {
                still = 0
                restarts++
                restartAt(player.currentPosition)
            }
        }
    }

    fun exit() {
        val pos = pending ?: player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        onExit(pos, dur)
    }

    fun flash(text: String) {
        toast = text
        scope.launch {
            delay(1_800)
            if (toast == text) toast = null
        }
    }

    fun nudge(dir: Int, repeat: Int) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        val base = pending ?: player.currentPosition
        pending = (base + dir * seekStep(repeat)).coerceIn(0L, (dur - 1_000L).coerceAtLeast(0L))
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(900)
            pending?.let { player.seekTo(it) }
            pending = null
        }
    }

    fun jumpPercent(n: Int) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        seekJob?.cancel()
        pending = null
        player.seekTo(dur * n / 10)
        flash("${n * 10} %")
    }

    fun togglePause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Wiedergabe nicht möglich (${e.errorCodeName})"
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) scope.launch { onExit(0, 0) }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }
        }
        player.addListener(l)
        player.setMediaItem(MediaItem.fromUri(p.url), p.startMs)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            seekJob?.cancel()
            player.removeListener(l)
            player.release()
        }
    }

    // Zeit/Fortschritt aktualisieren
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(500)
        }
    }
    // Leiste nach 5 s ausblenden (nicht während Pause oder Auswahl)
    LaunchedEffect(showUi, uiTick, isPlaying, picker) {
        if (showUi && isPlaying && picker == null) {
            delay(5_000)
            showUi = false
        }
    }
    LaunchedEffect(showUi, picker) {
        withFrameNanos { }
        runCatching { if (showUi && picker == null) playFocus.requestFocus() else if (!showUi) rootFocus.requestFocus() }
    }

    BackHandler {
        when {
            picker != null -> picker = null
            pending != null -> { seekJob?.cancel(); pending = null }
            showUi -> showUi = false
            else -> exit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                uiTick++
                val code = e.key.nativeKeyCode
                val repeat = e.nativeKeyEvent.repeatCount
                // Medientasten gehen immer
                when (code) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePause(); showUi = true; return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); showUi = true; return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { nudge(+1, repeat); return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { nudge(-1, repeat); return@onPreviewKeyEvent true }
                }
                if (code in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 && picker == null) {
                    jumpPercent(code - AndroidKeyEvent.KEYCODE_0)
                    return@onPreviewKeyEvent true
                }
                if (showUi || picker != null) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionLeft -> { nudge(-1, repeat); true }
                    e.key == Key.DirectionRight -> { nudge(+1, repeat); true }
                    isOkKey(e.key) -> {
                        if (pending != null) {
                            seekJob?.cancel()
                            pending?.let { player.seekTo(it) }
                            pending = null
                        } else {
                            togglePause()
                        }
                        showUi = true
                        true
                    }
                    e.key == Key.DirectionUp || e.key == Key.DirectionDown || code == AndroidKeyEvent.KEYCODE_MENU ||
                        code == AndroidKeyEvent.KEYCODE_INFO -> { showUi = true; true }
                    else -> false
                }
            },
    ) {
        val videoModifier = if (aspect.ratio != null) {
            Modifier.align(Alignment.Center).aspectRatio(aspect.ratio!!)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            modifier = videoModifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                }
            },
            update = { v ->
                v.resizeMode = when (aspect) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
        )

        if (buffering && error == null) {
            Text(
                "Lädt …",
                color = Palette.Text, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        toast?.let {
            Text(
                it,
                color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                    .background(Palette.Ground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Spulen ohne Leiste: große Zielzeit in der Mitte
        val target = pending
        if (target != null && !showUi) {
            val diff = target - position
            Column(
                Modifier.align(Alignment.Center)
                    .background(Palette.Ground.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(fmtTime(target), color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    (if (diff >= 0) "+" else "−") + fmtTime(kotlin.math.abs(diff)),
                    color = Palette.Highlight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (showUi || target != null || !isPlaying) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!isPlaying && target == null) Text("Pause", color = Palette.Highlight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                ProgressBar(position = position, target = target, buffered = buffered, duration = duration)
                Row {
                    Text(fmtTime(target ?: position), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (duration > 0) Text("−" + fmtTime(duration - (target ?: position)) + "  ·  " + fmtTime(duration), color = Palette.TextSoft, fontSize = 14.sp)
                }
                if (showUi) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvButton("⟲ 10 s", onClick = { uiTick++; nudge(-1, 0) })
                        TvButton(if (isPlaying) "❚❚ Pause" else "▶ Weiter", modifier = Modifier.focusRequester(playFocus), onClick = { uiTick++; togglePause() })
                        TvButton("30 s ⟳", onClick = { uiTick++; nudge(+1, 4) })
                        Spacer(Modifier.width(12.dp))
                        TvButton("Ton", onClick = { picker = C.TRACK_TYPE_AUDIO })
                        TvButton("Untertitel", onClick = { picker = C.TRACK_TYPE_TEXT })
                        TvButton("Bild: ${aspect.label}", onClick = {
                            uiTick++
                            aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size]
                        })
                    }
                    Text(
                        "◀ ▶ spulen (halten = schneller) · OK Pause · 0–9 springt auf 0–90 %",
                        color = Palette.TextMuted, fontSize = 12.sp,
                    )
                }
            }
        }

        val pk = picker
        if (pk != null) {
            val tv = tracksVersion
            val opts = remember(pk, tv) { TrackHelper.options(player, pk) }
            val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val first = remember(pk) { FocusRequester() }
            LaunchedEffect(pk) {
                withFrameNanos { }
                runCatching { first.requestFocus() }
            }
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 130.dp)
                    .width(300.dp)
                    .background(Palette.Panel.copy(alpha = 0.97f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (pk == C.TRACK_TYPE_AUDIO) "Tonspur" else "Untertitel", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (pk == C.TRACK_TYPE_TEXT) {
                    TvButton("Aus", selected = textOff, modifier = Modifier.fillMaxWidth().focusRequester(first), onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        picker = null
                    })
                }
                if (opts.isEmpty()) Text("Keine weiteren Spuren", color = Palette.TextMuted, fontSize = 13.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(opts) { i, o ->
                        val sel = o.selected && !(pk == C.TRACK_TYPE_TEXT && textOff)
                        TvButton(
                            (if (sel) "✓ " else "") + o.label,
                            selected = sel,
                            modifier = Modifier.fillMaxWidth().then(if (i == 0 && pk == C.TRACK_TYPE_AUDIO) Modifier.focusRequester(first) else Modifier),
                            onClick = {
                                TrackHelper.select(player, o)
                                picker = null
                            },
                        )
                    }
                }
            }
        }

        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(it, color = Palette.Text, fontSize = 18.sp)
                TvButton("Zurück", onClick = { exit() })
            }
        }
    }
}

@Composable
private fun ProgressBar(position: Long, target: Long?, buffered: Long, duration: Long) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f))) {
        if (duration > 0) {
            val f = { v: Long -> (v.toFloat() / duration).coerceIn(0f, 1f) }
            Box(Modifier.fillMaxWidth(f(buffered)).fillMaxHeight().background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(f(position)).fillMaxHeight().background(Palette.Highlight))
            if (target != null) {
                Box(Modifier.fillMaxWidth(f(target)).fillMaxHeight()) {
                    Box(Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().background(Color.White))
                }
            }
        }
    }
}
