package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import de.aze.iptv.AppController
import de.aze.iptv.data.Episode
import de.aze.iptv.data.Http
import de.aze.iptv.data.SeriesDetails
import de.aze.iptv.data.SourceType
import de.aze.iptv.data.VodCategory
import de.aze.iptv.data.VodDetails
import de.aze.iptv.data.VodItem
import de.aze.iptv.data.XtreamVod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class Playing(val url: String, val title: String, val resumeKey: String, val startMs: Long)

/**
 * Filme & Serien im TiviMate-Stil:
 *  links die Kategorien, rechts die Poster; OK öffnet die Detailseite mit Beschreibung,
 *  bei Serien mit Staffeln und Folgen; "Weiterschauen" merkt sich die Stelle.
 */
@Composable
fun VodScreen(app: AppController, startWithSeries: Boolean, onExit: () -> Unit) {
    val xtreams = app.sources.filter { it.type == SourceType.XTREAM }
    var series by remember { mutableStateOf(startWithSeries) }
    var sourceIdx by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<VodItem?>(null) }
    var playing by remember { mutableStateOf<Playing?>(null) }

    if (xtreams.isEmpty()) {
        BackHandler(onBack = onExit)
        Box(Modifier.fillMaxSize().background(Palette.Ground).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Filme & Serien gibt es bei Playlists mit Server + Login (Xtream). Bitte in den Einstellungen so eine Playlist hinzufügen.",
                    color = Palette.TextSoft, fontSize = 18.sp,
                )
                TvButton("Zurück zum Fernsehen", onClick = onExit)
            }
        }
        return
    }
    val src = xtreams[sourceIdx.coerceIn(0, xtreams.lastIndex)]
    val api = remember(src.id) { XtreamVod(src) }

    val p = playing
    if (p != null) {
        VodPlayer(p, onExit = { pos, dur ->
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

    BackHandler(onBack = onExit)
    Browse(
        app = app, api = api, series = series, sourceId = src.id,
        sourceLabel = if (xtreams.size > 1) src.name else null,
        onSwitch = { series = it },
        onNextSource = { sourceIdx = (sourceIdx + 1) % xtreams.size },
        onOpen = { detail = it },
        onExit = onExit,
    )
}

@Composable
private fun Browse(
    app: AppController,
    api: XtreamVod,
    series: Boolean,
    sourceId: String,
    sourceLabel: String?,
    onSwitch: (Boolean) -> Unit,
    onNextSource: () -> Unit,
    onOpen: (VodItem) -> Unit,
    onExit: () -> Unit,
) {
    var cats by remember(api, series) { mutableStateOf<List<VodCategory>?>(null) }
    var selected by remember(api, series) { mutableIntStateOf(0) }
    var items by remember(api, series) { mutableStateOf<List<VodItem>?>(null) }
    var error by remember(api, series) { mutableStateOf<String?>(null) }
    val firstCat = remember { FocusRequester() }

    LaunchedEffect(api, series) {
        cats = runCatching { if (series) api.seriesCategories() else api.movieCategories() }
            .onFailure { error = "Kategorien konnten nicht geladen werden." }
            .getOrDefault(emptyList())
        withFrameNanos { }
        runCatching { firstCat.requestFocus() }
    }
    val catList = cats.orEmpty()
    LaunchedEffect(api, series, selected, cats) {
        val c = catList.getOrNull(selected) ?: return@LaunchedEffect
        items = null
        delay(250) // erst laden, wenn man kurz auf einer Kategorie stehen bleibt
        items = runCatching { if (series) api.series(c.id) else api.movies(c.id) }
            .onFailure { error = "Inhalte konnten nicht geladen werden." }
            .getOrDefault(emptyList())
    }

    Row(Modifier.fillMaxSize().background(Palette.Ground)) {
        Column(
            Modifier.width(250.dp).fillMaxHeight().background(Palette.PanelDark).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TvButton("Filme", selected = !series, onClick = { onSwitch(false) })
                TvButton("Serien", selected = series, onClick = { onSwitch(true) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                TvButton("Live-TV", onClick = onExit)
                if (sourceLabel != null) TvButton(sourceLabel, onClick = onNextSource)
            }
            if (cats == null) Text("Lade Kategorien …", color = Palette.TextMuted, fontSize = 13.sp)
            error?.let { Text(it, color = Palette.Live, fontSize = 13.sp) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                itemsIndexed(catList) { i, c ->
                    Focusable(
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstCat) else Modifier),
                        onClick = { selected = i },
                        onFocus = { selected = i },
                    ) { focused ->
                        Text(
                            c.name,
                            color = if (focused) Palette.OnAccent else if (i == selected) Palette.Accent else Palette.Text,
                            fontSize = 14.sp,
                            fontWeight = if (focused || i == selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (focused) Palette.Accent else Color.Transparent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
            val list = items
            when {
                list == null -> Text("Lade …", color = Palette.TextMuted, fontSize = 15.sp)
                list.isEmpty() -> Text("Keine Einträge in dieser Kategorie.", color = Palette.TextMuted, fontSize = 15.sp)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        PosterCard(item, resumed = !series && app.resumeOf("m:$sourceId:${item.id}") > 0) { onOpen(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterCard(item: VodItem, resumed: Boolean, onClick: () -> Unit) {
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
                        model = item.poster,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
                fontSize = 13.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailView(
    app: AppController,
    api: XtreamVod,
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
                val meta = listOfNotNull(
                    i?.year ?: item.year,
                    (i?.rating ?: item.rating)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" },
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

/** Film-/Folgen-Wiedergabe mit den Media3-Bedienelementen: Spulen mit ◀ ▶ bzw. ◀◀ ▶▶, OK = Pause. */
@OptIn(UnstableApi::class)
@Composable
private fun VodPlayer(p: Playing, onExit: (posMs: Long, durMs: Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember {
        val http = DefaultHttpDataSource.Factory().setUserAgent(Http.USER_AGENT).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }
    fun exit() {
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        onExit(pos, dur)
    }
    BackHandler { exit() }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Wiedergabe nicht möglich (${e.errorCodeName})"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) scope.launch { onExit(0, 0) }
            }
        }
        player.addListener(l)
        player.setMediaItem(MediaItem.fromUri(p.url), p.startMs)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(l)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3_000
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    post { requestFocus() }
                }
            },
        )
        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(it, color = Palette.Text, fontSize = 18.sp)
                TvButton("Zurück", onClick = { exit() })
            }
        }
        Spacer(Modifier.height(1.dp))
    }
}
