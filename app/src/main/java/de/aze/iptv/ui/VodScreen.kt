package de.aze.iptv.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Playing(
    val url: String,
    val title: String,
    val resumeKey: String,
    val startMs: Long,
    /** für die Weiterschauen-Reihe */
    val cont: de.aze.iptv.data.ContinueEntry? = null,
    /** Serie: nächste Folge („Nächste Folge in 10 Sekunden“) – null = keine */
    val next: (() -> Playing)? = null,
    val nextLabel: String? = null,
)

/** Eigene Listen ganz oben in den Kategorien (keine Anbieter-Kategorien) */
private const val CAT_CONTINUE = "@weiter"
private const val CAT_WATCH = "@merk"
private const val CAT_DL = "@geladen"
private const val CAT_NEW = "@neu"

/**
 * Filme & Serien – bedient sich wie Live-TV:
 *  links die Kategorien (mit Anzahl), rechts die Poster (durchnummeriert);
 *  ◀ in den Kategorien öffnet das Menü (TV, Filme, Serien, Suchen, Einstellungen).
 */
@Composable
fun VodScreen(
    app: AppController, startWithSeries: Boolean, onExit: () -> Unit, onOpenSettings: () -> Unit = onExit,
    onFootball: (() -> Unit)? = null, onQuit: (() -> Unit)? = null, onSkorText: (() -> Unit)? = null,
) {
    val vodSources = app.vodSources
    var series by remember { mutableStateOf(startWithSeries) }
    // Emulator-Test: gleich den Test-Anbieter für Filme wählen
    var sourceIdx by remember { mutableIntStateOf(if (app.testOpenVod) vodSources.indexOfFirst { it.name == "Test-VOD" }.coerceAtLeast(0) else 0) }
    // Details: Liste, aus der der Film kam (Kategorie oder Suche), und welcher davon – am Handy/Tablet wischt man darin
    var detailList by remember { mutableStateOf<List<VodItem>?>(null) }
    var detailIdx by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Playing?>(null) }
    // Stelle im Katalog merken: nach Details oder Film zurück wieder in derselben Kategorie beim selben Eintrag landen
    val browseMem = remember { BrowseMemory() }

    if (vodSources.isEmpty()) {
        BackHandler(onBack = onExit)
        Box(Modifier.fillMaxSize().background(Palette.ScreenBrush).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Noch keine Playlist vorhanden.", color = Palette.TextSoft, fontSize = 18.sp)
                TvButton("Zurück zum Fernsehen", onClick = onExit)
            }
        }
        return
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        CoverUrls.vodOpen = true
        onDispose { CoverUrls.vodOpen = false }
    }
    val src = vodSources[sourceIdx.coerceIn(0, vodSources.lastIndex)]
    val api = remember(src.id, src.url, src.user) { app.vodProvider(src) }

    val p = playing
    if (p != null) {
        // neuer Player je Film/Folge (nächste Folge = neuer Eintrag)
        androidx.compose.runtime.key(p.url, p.resumeKey) {
            VodPlayer(
                app, p,
                onExit = { pos, dur ->
                    app.saveResume(p.resumeKey, pos, dur)
                    p.cont?.let { app.noteContinue(it) }
                    playing = null
                },
                onNext = { np ->
                    // diese Folge ist zu Ende geschaut
                    app.saveResume(p.resumeKey, 0, 0)
                    p.cont?.let { app.noteContinue(it) }
                    qaLog("VOD_NEXT ${np.title}")
                    playing = np
                },
            )
        }
        return
    }

    val dl = detailList
    if (dl != null && detailIdx in dl.indices) {
        // Handy/Tablet: ↑ wischen = nächster Film der Kategorie, ↓ = voriger, ← → = zurück zur Kachel-Übersicht
        SwipeDetail(
            enabled = Device.isTouch, count = dl.size, index = detailIdx, kind = if (series) "Serie" else "Film",
            onIndex = { n ->
                detailIdx = n
                // zurück in der Übersicht: dort, wo man zuletzt war
                browseMem.openIdx = n
                qaLog("VOD_WISCHEN ${n + 1}/${dl.size} ${dl[n].name}")
            },
            onBack = { detailList = null },
        ) { idx ->
            DetailView(
                app = app, api = api, item = dl[idx], series = series, sourceId = src.id,
                onPlay = { playing = it },
                onBack = { detailList = null },
            )
        }
        return
    }

    Browse(
        mem = browseMem,
        app = app, api = api, series = series, sourceId = src.id,
        sourceLabel = src.name,
        multi = vodSources.size > 1,
        onSwitch = { series = it },
        onNextSource = { sourceIdx = (sourceIdx + 1) % vodSources.size },
        onOpen = { list, idx -> detailList = list; detailIdx = idx },
        onExit = onExit,
        onOpenSettings = onOpenSettings,
        onFootball = onFootball,
        onSkorText = onSkorText,
        onQuit = onQuit,
    )
}

/** Wo man im Katalog zuletzt war (kein Compose-Zustand: wird nur beim Wiederaufbau der Übersicht gelesen). */
private class BrowseMemory {
    var api: Any? = null
    var series = false
    var selected = 0
    var selectedId: String? = null
    var query: String? = null
    var openIdx = -1
    var openAt = ""
}

@Composable
private fun Browse(
    mem: BrowseMemory,
    app: AppController,
    api: VodProvider,
    series: Boolean,
    sourceId: String,
    sourceLabel: String,
    multi: Boolean,
    onSwitch: (Boolean) -> Unit,
    onNextSource: () -> Unit,
    onOpen: (List<VodItem>, Int) -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    onFootball: (() -> Unit)? = null,
    onSkorText: (() -> Unit)? = null,
    onQuit: (() -> Unit)? = null,
) {
    val keep = mem.api === api && mem.series == series
    if (!keep) { mem.api = api; mem.series = series; mem.selected = 0; mem.selectedId = null; mem.query = null; mem.openIdx = -1 }
    var cats by remember(api, series) { mutableStateOf<List<VodCategory>?>(null) }
    // gewählte Kategorie per id: oben kommen eigene Listen dazu oder fallen weg (Weiterschauen leer), die Auswahl bleibt
    var selectedId by remember(api, series) { mutableStateOf(mem.selectedId) }
    var items by remember(api, series) { mutableStateOf<List<VodItem>?>(null) }
    var error by remember(api, series) { mutableStateOf<String?>(null) }
    var counts by remember(api, series) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var menu by remember { mutableStateOf(false) }
    var askSearch by remember { mutableStateOf(false) }
    val openSearch: () -> Unit = {
        qaLog("VOD_SUCHE_LANG")
        askSearch = true
    }
    var query by remember(api, series) { mutableStateOf<String?>(mem.query) }
    var results by remember(api, series) { mutableStateOf<List<VodItem>?>(null) }
    mem.query = query
    val catFocus = remember { FocusRequester() }
    val openFocus = remember { FocusRequester() }
    // eigene Suche (nur Filme bzw. nur Serien): Zeile ganz oben über den Kategorien, erster Treffer
    val searchFocus = remember { FocusRequester() }
    val firstHitFocus = remember { FocusRequester() }
    var focusHits by remember { mutableStateOf(false) }
    // +1: über den Kategorien steht die Suchzeile
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (mem.selected + 1 - 3).coerceAtLeast(0))
    val kind = if (series) "Serien" else "Filme"
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    BackHandler {
        when {
            menu -> onExit()
            query != null -> {
                // Suche beenden: zurück zur Suchzeile (der Treffer mit dem Fokus verschwindet gleich)
                query = null
                scope.launch { withFrameNanos { }; runCatching { searchFocus.requestFocus() } }
            }
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
    val ctx = LocalContext.current
    LaunchedEffect(api, series) {
        // erst wenn die erste Kategorie da ist – sonst konkurriert die große Gesamtliste mit ihr (und mit den Covern)
        androidx.compose.runtime.snapshotFlow { items }.first { it != null }
        delay(1_500)
        counts = runCatching { api.counts(series) }.getOrDefault(emptyMap())
        // Danach im Hintergrund die Cover der bevorzugten Sprache (z. B. erst alle deutschen, dann türkischen
        // Kategorien), dann der Rest – in kleinen Portionen, damit die Leitung frei bleibt.
        val pref = app.vodLang
        if (pref == 2) return@LaunchedEffect
        val all = runCatching { api.all(series) }.getOrNull() ?: return@LaunchedEffect
        // Ausgeblendete Kategorien (und Erwachsenen-Filter) werden gar nicht erst geladen
        val skip = cats.orEmpty().filter {
            app.isVodHidden(sourceId, series, it.id) || (app.adultFilter && de.aze.iptv.data.AdultFilter.isAdult(it.name))
        }.map { it.id }.toSet()
        val langOf = cats.orEmpty().associate { it.id to vodLanguage(it.name) }
        val order = if (pref == 0) listOf("de", "tr") else listOf("tr", "de")
        // Nicht die ganze Bibliothek vorladen (überlastet Leitung und Anbieter-Server):
        // nur die ersten Reihen (24 Cover) jeder Kategorie der bevorzugten Sprache, höchstens 240.
        delay(8_000)
        // Durchsuchen der ganzen Bibliothek nicht im Bedien-Teil (sonst ruckelt das Blättern genau dann)
        val queue = withContext(kotlinx.coroutines.Dispatchers.Default) {
            val visible = all.filter { it.cat !in skip }
            order.flatMap { l -> visible.filter { langOf[it.cat] == l }.groupBy { it.cat }.values.flatMap { it.take(24) } }
                .mapNotNull { it.poster }.distinct().take(240)
        }
        qaLog("VOD_PREFETCH ${queue.size} Cover (${order.joinToString(" → ")} zuerst)")
        CoverUrls.prefetch(ctx, queue) { posterRequest(ctx, it) }
    }
    // Erwachsenen-Filter: solche Kategorien gar nicht erst zeigen
    val adultOff = app.adultFilter
    val allCats = remember(cats, adultOff) {
        cats.orEmpty().let { l -> if (adultOff) l.filterNot { de.aze.iptv.data.AdultFilter.isAdult(it.name) } else l }
    }
    // Vom Nutzer ausgeblendete Kategorien (Dialog „Kategorien ausblenden“)
    val hiddenVod = app.vodHidden
    val catList = remember(allCats, hiddenVod, sourceId, series) {
        allCats.filterNot { app.isVodHidden(sourceId, series, it.id) }
    }
    // Eigene Listen ganz oben (Aziz 25.11.): Weiterschauen, Merkliste, Neu hinzugefügt
    val contEntries = app.continueFor(sourceId, series)
    val watchRefs = app.watchFor(sourceId, series)
    val newOk = remember(cats) { cats != null && api.supportsNew() }
    val hasCont = contEntries.isNotEmpty()
    val hasWatch = watchRefs.isNotEmpty()
    // Heruntergeladen (Aziz 26.11.): Filme bzw. Serien dieser Playlist, die (ganz oder teilweise) auf dem Gerät liegen
    val dlEntries = de.aze.iptv.data.Downloader.entries
    val dlRefs = remember(dlEntries, sourceId, series) {
        dlEntries.filter { it.ref.sourceId == sourceId && it.ref.series == series }.sortedByDescending { it.added }
            .map { it.ref }.distinctBy { it.id }
    }
    val hasDl = dlRefs.isNotEmpty()
    val shownCats = remember(hasCont, hasWatch, hasDl, newOk, catList) {
        buildList {
            if (hasCont) add(VodCategory(CAT_CONTINUE, "▶ Weiterschauen"))
            if (hasDl) add(VodCategory(CAT_DL, "⬇ Heruntergeladen"))
            if (hasWatch) add(VodCategory(CAT_WATCH, "★ Merkliste"))
            if (newOk) add(VodCategory(CAT_NEW, "✦ Neu hinzugefügt"))
            addAll(catList)
        }
    }
    // Anfang (oder gewählte Liste fällt weg): Weiterschauen, sonst die erste Kategorie des Anbieters –
    // nicht „Neu hinzugefügt“, das braucht die ganze Bibliothek und wäre beim Öffnen zu langsam
    val catKeys = remember(shownCats) {
        val seen = HashMap<String, Int>()
        shownCats.map { c -> val n = (seen[c.id] ?: 0) + 1; seen[c.id] = n; if (n == 1) c.id else "${c.id}#$n" }
    }
    val selected = shownCats.indexOfFirst { it.id == selectedId }.let { at ->
        if (at >= 0) at else shownCats.indexOfFirst { it.id == CAT_CONTINUE || !it.id.startsWith("@") }.coerceAtLeast(0)
    }
    val selCat = shownCats.getOrNull(selected)
    mem.selected = selected
    mem.selectedId = selCat?.id ?: selectedId
    LaunchedEffect(selCat?.id, cats) {
        if (cats != null && selCat != null && selCat.id != selectedId) selectedId = selCat.id
    }
    // eigene Liste geändert (z. B. Eintrag entfernt): neu anzeigen
    val specialData: Any? = when (selCat?.id) {
        CAT_CONTINUE -> contEntries
        CAT_WATCH -> watchRefs
        CAT_DL -> dlRefs
        else -> null
    }
    LaunchedEffect(api, series, selCat?.id, cats, catList, specialData) {
        val c = selCat ?: return@LaunchedEffect
        // Altes Raster bleibt kurz stehen: ist die Kategorie schnell da (Gesamtliste im Speicher), gibt es kein
        // „Lade …“-Aufblitzen; erst wenn es dauert, kommt die Ladeanzeige.
        delay(150) // erst laden, wenn man kurz auf einer Kategorie stehen bleibt
        val t0 = System.currentTimeMillis()
        val showLoading = launch { delay(250); items = null }
        val adultOn = app.adultFilter
        items = runCatching {
            val l = when (c.id) {
                CAT_CONTINUE -> contEntries.map { it.ref.toItem() }
                CAT_WATCH -> watchRefs.map { it.toItem() }
                CAT_DL -> dlRefs.map { it.toItem() }
                CAT_NEW -> {
                    // die neuesten Titel des Anbieters – ohne ausgeblendete Kategorien
                    val all = api.all(series)
                    val shown = catList.map { it.id }.toSet()
                    withContext(kotlinx.coroutines.Dispatchers.Default) {
                        de.aze.iptv.data.VodLibrary.newest(all, { it.cat == null || it.cat in shown })
                    }
                }
                else -> if (series) api.series(c.id) else api.movies(c.id)
            }
            if (adultOn) withContext(kotlinx.coroutines.Dispatchers.Default) { l.filterNot { de.aze.iptv.data.AdultFilter.isAdult(it.name) } } else l
        }
            // abgebrochen (Kategorie gewechselt): nicht als Fehler werten und die neue Liste nicht überschreiben
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .onSuccess { error = null; qaLog("VOD_CAT ${c.name} ${it.size} in ${System.currentTimeMillis() - t0} ms") }
            .onFailure { error = "Inhalte konnten nicht geladen werden."; qaLog("VOD_CAT_ERROR ${c.name} ${it.javaClass.simpleName}") }
            .getOrDefault(emptyList())
        showLoading.cancel()
    }
    LaunchedEffect(query, catList) {
        val q = query ?: return@LaunchedEffect
        if (cats == null) return@LaunchedEffect // Kategorien noch nicht da (Rückkehr in eine Suche)
        results = null
        // Nur in Filmen bzw. Serien dieser Playlist, in allen Kategorien – ohne ausgeblendete Kategorien und ohne
        // Titel, die der Erwachsenen-Filter verbirgt (siehe VodSearch)
        val shown = catList.map { it.id }.toSet()
        val adultOn = app.adultFilter
        val t0 = System.currentTimeMillis()
        val all = runCatching { api.all(series) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
        // ganze Bibliothek durchsuchen: nicht im Bedien-Teil
        results = withContext(kotlinx.coroutines.Dispatchers.Default) {
            de.aze.iptv.data.VodSearch.search(all, q, { it.name }) {
                (it.cat == null || it.cat in shown) && !(adultOn && de.aze.iptv.data.AdultFilter.isAdult(it.name))
            }
        }
        qaLog("VOD_SEARCH „$q“ ${results?.size} Treffer (${if (series) "Serien" else "Filme"}, ${all.size} durchsucht) in ${System.currentTimeMillis() - t0} ms")
    }
    LaunchedEffect(menu) {
        if (!menu) {
            withFrameNanos { }
            runCatching { catFocus.requestFocus() }
        }
    }

    val ctxTouch = LocalContext.current
    val touchDevice = Device.isTouch
    Column(Modifier.fillMaxSize().background(Palette.ScreenBrush)) {
    // Tablet/Handy: Leiste oben zum Wechseln (auf dem Fernseher über ◀ im Seitenmenü)
    if (touchDevice) {
        Row(
            Modifier.fillMaxWidth().background(Palette.PanelBrush).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvButton("📺 Live-TV", onClick = onExit)
            TvButton("Filme", selected = !series, onClick = { query = null; onSwitch(false) })
            TvButton("Serien", selected = series, onClick = { query = null; onSwitch(true) })
            TvButton("🔍 $kind suchen", onClick = { askSearch = true })
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
                    // hier sucht „Suchen“ nur in Filmen bzw. Serien – so steht es auch da
                    searchLabel = "$kind suchen",
                    onSettings = onOpenSettings,
                    onPlaylist = { menu = false; query = null; onNextSource() },
                    onRight = { menu = false },
                    // dasselbe Menü wie beim Fernsehen – auch „Fußball heute“ und „App beenden“
                    onFootball = onFootball,
                    onSkorText = onSkorText,
                    onQuit = onQuit,
                )
            }
            Column(
                Modifier
                    .width(minOf(260, (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.32f).toInt()).dp)
                    .fillMaxHeight()
                    // wie die Gruppenübersicht beim Fernsehen
                    .background(if (menu) Palette.GroupBrush else Palette.PanelBrush)
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
                if (cats != null && shownCats.isEmpty() && error == null) {
                    Text("Keine $kind in dieser Playlist.", color = Palette.TextMuted, fontSize = 13.sp)
                    api.sourceInfo().takeIf { it.isNotBlank() }?.let { Text("Quelle: $it", color = Palette.TextMuted, fontSize = 11.sp) }
                }
                LazyColumn(state = catListState, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    // Ganz oben: eigene Suche – ▲ aus der ersten Kategorie oder antippen
                    if (cats != null && shownCats.isNotEmpty()) item(key = "suche") {
                        VodSearchRow(kind = kind, query = query, modifier = Modifier.focusRequester(searchFocus)) { askSearch = true }
                    }
                    // Schlüssel = id (manche Anbieter liefern eine id doppelt → mit Zähler); nicht die Stelle, die
                    // verschiebt sich, wenn oben Weiterschauen/Merkliste dazukommen oder wegfallen
                    itemsIndexed(shownCats, key = { i, c -> catKeys.getOrElse(i) { "$i|${c.id}" } }) { i, c ->
                        val own = c.id.startsWith("@")
                        Focusable(
                            modifier = Modifier.fillMaxWidth().then(if (i == selected) Modifier.focusRequester(catFocus) else Modifier),
                            onClick = { selectedId = c.id; query = null },
                            // OK lang gedrückt: gleich die Suche (Aziz 25.11. – nicht erst nach oben zur Suchzeile)
                            onLongClick = openSearch,
                            // Nur beim Wechsel der Kategorie die Suche beenden (nicht wenn der Fokus nach der Suche zurückkommt)
                            onFocus = { if (i != selected) { selectedId = c.id; query = null } },
                            qaName = "K ${c.name}",
                        ) { focused ->
                            val count = when (c.id) {
                                CAT_CONTINUE -> contEntries.size
                                CAT_WATCH -> watchRefs.size
                                CAT_DL -> dlRefs.size
                                else -> counts[c.id] ?: items?.size?.takeIf { i == selected }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            focused -> Palette.Accent
                                            i == selected -> Palette.Highlight.copy(alpha = 0.16f)
                                            else -> Color.Transparent
                                        },
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    c.name,
                                    // eigene Listen (Weiterschauen, Merkliste, Neu) in der Hervorhebungsfarbe
                                    color = if (focused) Palette.OnAccent else if (own) Palette.Highlight else Palette.TextSoft,
                                    fontSize = 15.sp,
                                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (count != null) {
                                    Text(
                                        "$count",
                                        color = if (focused) Palette.OnAccent else Palette.TextMuted,
                                        fontSize = 13.sp,
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
                // Erwachsenen-Filter läuft schon beim Laden (im Hintergrund), nicht hier beim Zeichnen
                val list = raw
                val title = if (q != null) "Suche: „$q“ · alle Kategorien" + (if (multi) " · $sourceLabel" else "") else selCat?.name.orEmpty()
                // Weiterschauen/Merkliste: einzelne Einträge entfernen (Menütaste; am Handy in den Details)
                val removable = q == null && (selCat?.id == CAT_CONTINUE || selCat?.id == CAT_WATCH)
                // Vorschau oben (wie im Entwurf): zeigt den gewählten Film – sonst den ersten der Liste
                val heroState = remember(list) { mutableStateOf<VodItem?>(null) }
                Column {
                    // Vorschau nur mit Fernbedienung: am Handy/Tablet startet Antippen den Film sofort, die Leiste
                    // zeigte dort nur den ersten Film und nahm Platz weg (Aziz) → nur Kacheln
                    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
                    if (!Device.isTouch && !list.isNullOrEmpty() && cfg.screenHeightDp >= 480 && cfg.screenWidthDp >= 600) {
                        VodHeroSlot(app, api, heroState, list.first(), series, sourceId, title)
                    }
                    // Oben: Kategorie bzw. Suche und rechts die Anzahl
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            color = Palette.TextSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (list != null) Text("${list.size} $kind", color = Palette.Highlight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    if (removable && !list.isNullOrEmpty()) {
                        Text(
                            Device.hint("Menütaste ☰ auf einem Cover: aus der Liste entfernen", "Entfernen: Cover antippen → in den Details"),
                            color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    when {
                        list == null -> Text(if (q != null) "Suche …" else "Lade …", color = Palette.TextMuted, fontSize = 15.sp)
                        list.isEmpty() -> Text(
                            if (q != null) "Nichts gefunden. Ausgeblendete Kategorien werden nicht durchsucht." else "Keine Einträge in dieser Kategorie.",
                            color = Palette.TextMuted, fontSize = 15.sp,
                        )
                        else -> {
                          // Rückkehr aus Details/Film: wieder zum geöffneten Eintrag (nur in derselben Kategorie/Suche)
                          val restore = remember(list) { if (mem.openIdx in list.indices && mem.openAt == "${selCat?.id}|$q") mem.openIdx else -1 }
                          val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(initialFirstVisibleItemIndex = restore.coerceAtLeast(0))
                          // neue Suche: gleich auf den ersten Treffer (mit der Fernbedienung direkt weiter blättern)
                          LaunchedEffect(list) {
                              if (q == null || !focusHits || restore >= 0) return@LaunchedEffect
                              focusHits = false
                              repeat(12) {
                                  withFrameNanos { }
                                  if (runCatching { firstHitFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                              }
                          }
                          LaunchedEffect(list) {
                              if (restore < 0) return@LaunchedEffect
                              mem.openIdx = -1
                              repeat(12) {
                                  withFrameNanos { }
                                  if (runCatching { openFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                              }
                          }
                          // Poster der nächsten Reihen schon vorladen, damit beim schnellen Blättern Bilder da sind
                          val ctx = LocalContext.current
                          val vodFast = app.vodFast
                          // Vorrat holt die Cover dieser Kategorie jetzt zuerst (ab der sichtbaren Stelle)
                          androidx.compose.runtime.DisposableEffect(list) {
                              CoverUrls.focusList = list; CoverUrls.focusSeries = series; CoverUrls.focusFrom = 0
                              onDispose { if (CoverUrls.focusList === list) CoverUrls.focusList = null }
                          }
                          LaunchedEffect(gridState) {
                              androidx.compose.runtime.snapshotFlow { gridState.firstVisibleItemIndex }.collect { CoverUrls.focusFrom = it }
                          }
                          LaunchedEffect(list, gridState) {
                              val requested = HashSet<String>()
                              val loader = coil.Coil.imageLoader(ctx)
                              // Nur wenige Cover direkt hinter dem sichtbaren Bereich vorladen, nie die ganze Kategorie.
                              // collectLatest: erst wenn das Blättern 300 ms ruht (sonst drängeln sie sich vor die sichtbaren)
                              androidx.compose.runtime.snapshotFlow { gridState.firstVisibleItemIndex }.collectLatest {
                                  kotlinx.coroutines.delay(300)
                                  val from = ((gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1).coerceAtMost(list.size)
                                  list.subList(from, list.size).take(if (vodFast) 24 else 12).forEach { item ->
                                      val url = item.poster ?: return@forEach
                                      if (requested.add(url)) loader.enqueue(posterRequest(ctx, url, item.name, series))
                                  }
                              }
                          }
                          LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItemsIndexed(list, key = { i, it -> "$i|${it.id}" }) { idx, item ->
                                PosterCard(
                                    item, number = idx + 1, resumed = !series && app.resumeOf("m:$sourceId:${item.id}") > 0, series = series,
                                    onFocus = { heroState.value = item },
                                    // OK lang gedrückt: Suche öffnen, egal wie weit unten man in den Kacheln ist
                                    onLongClick = openSearch,
                                    modifier = when {
                                        idx == restore -> Modifier.focusRequester(openFocus)
                                        idx == 0 && q != null -> Modifier.focusRequester(firstHitFocus)
                                        else -> Modifier
                                    }.then(
                                        if (!removable) Modifier else Modifier.onPreviewKeyEvent { e ->
                                            if (e.key.nativeKeyCode != AndroidKeyEvent.KEYCODE_MENU) return@onPreviewKeyEvent false
                                            if (e.type == KeyEventType.KeyUp) {
                                                // danach steht der Fokus auf dem Cover, das nachrückt
                                                mem.openIdx = idx.coerceAtMost(list.size - 2).coerceAtLeast(0)
                                                mem.openAt = "${selCat?.id}|$q"
                                                if (selCat?.id == CAT_CONTINUE) app.removeContinue(sourceId, series, item.id)
                                                else app.toggleWatch(de.aze.iptv.data.VodRef.of(sourceId, series, item))
                                                // letzter Eintrag weg: die Liste verschwindet → Markierung auf die Kategorien
                                                // (erst nach dem Neuaufbau, sonst hinge sie noch an der alten Zeile)
                                                if (list.size <= 1) scope.launch { repeat(3) { withFrameNanos { } }; runCatching { catFocus.requestFocus() } }
                                            }
                                            true
                                        },
                                    ),
                                ) { mem.openIdx = idx; mem.openAt = "${selCat?.id}|$q"; onOpen(list, idx) }
                            }
                          }
                        }
                    }
                }
            }
        }

        if (askSearch) {
            InputDialog("$kind suchen – in allen Kategorien", query.orEmpty(), password = false, keyboard = KeyboardType.Text, onDone = {
                askSearch = false
                query = it.trim().ifEmpty { null }
                focusHits = query != null
            }, onCancel = { askSearch = false })
        }
    }
    }
}

/**
 * Suchzeile ganz oben in den Kategorien (eigene Suche nur in Filmen bzw. Serien). Ohne Suche „🔍 Filme suchen“,
 * mit Suche der Suchbegriff (OK ändert ihn, Zurück beendet die Suche).
 */
@Composable
private fun VodSearchRow(kind: String, query: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(modifier = modifier.fillMaxWidth().padding(bottom = 6.dp), qaName = "VOD_SUCHE", onClick = onClick, onLongClick = onClick) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        focused -> Palette.Accent
                        query != null -> Palette.Highlight.copy(alpha = 0.16f)
                        else -> Palette.Highlight.copy(alpha = 0.07f)
                    },
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 15.sp, modifier = Modifier.padding(end = 8.dp))
            Text(
                if (query != null) "„$query“" else "$kind suchen",
                color = when {
                    focused -> Palette.OnAccent
                    query != null -> Palette.Highlight
                    else -> Palette.TextSoft
                },
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (query != null && focused) Text("OK ändern", color = Palette.OnAccent, fontSize = 11.sp)
        }
    }
}

/**
 * Kleine Cover-Variante: Viele Anbieter verlinken TMDB-Bilder in voller Größe (oft 200–500 KB). TMDB liefert dieselben
 * Bilder auch klein (w185, ca. 15 KB) – für die Kacheln reicht das völlig und lädt ein Vielfaches schneller.
 */
private val tmdbSize = Regex("(image\\.tmdb\\.org/t/p/)(original|w\\d+(_and_h\\d+[^/]*)?|h\\d+)/")
internal fun smallPoster(url: String): String = CoverUrls.small(url, 200, 300)

/**
 * Cover-Adressen: TMDB-Bilder in kleiner Größe; Cover vom Server des Anbieters über images.weserv.nl
 * (holt das Bild einmal ab, verkleinert es auf Kachelgröße und liefert es von schnellen Servern).
 * Klappt das nicht, lädt die App das Original direkt (siehe CoverFallback in MainActivity).
 */
object CoverUrls {
    const val PROXY = "https://images.weserv.nl/"
    @Volatile var proxy = false
    @Volatile var tmdbFirst = false
    /** Filme & Serien gerade offen → Vorrat-Auffüllen pausiert */
    @Volatile var vodOpen = false
    /** gerade offene Kategorie (Filme & Serien offen): deren Cover holt der Vorrat zuerst, ab der sichtbaren Stelle */
    @Volatile var focusList: List<VodItem>? = null
    @Volatile var focusSeries = false
    @Volatile var focusFrom = 0
    /** Zeitpunkt des letzten Umschaltens – der Vorrat pausiert danach kurz */
    @Volatile var lastZap = 0L
    /** eigener Bildlader nur fürs Vorladen (schmale Leitung, siehe MainActivity) */
    @Volatile var bg: coil.ImageLoader? = null

    /** Cover nacheinander im Hintergrund holen (2 gleichzeitig) – blockiert nie die sichtbaren Cover */
    suspend fun prefetch(ctx: android.content.Context, urls: List<String>, request: (String) -> coil.request.ImageRequest) {
        val loader = bg ?: return
        kotlinx.coroutines.coroutineScope {
            urls.chunked(2).forEach { pair ->
                pair.map { u -> async { runCatching { loader.execute(request(u)) } } }.forEach { it.await() }
            }
        }
    }

    fun small(url: String, w: Int, h: Int, force: Boolean = false): String {
        if (url.contains("image.tmdb.org/t/p/")) return url.replace(tmdbSize, when { w <= 200 -> "$1w185/"; w <= 400 -> "$1w342/"; else -> "$1w780/" })
        if (!(proxy || force) || !url.startsWith("http")) return url
        // Adressen mit Zugangsdaten nie an einen fremden Dienst geben
        val low = url.lowercase()
        if ("username=" in low || "password=" in low || "token=" in low || url.substringAfter("://").substringBefore("/").contains("@")) return url
        return PROXY + "?url=" + java.net.URLEncoder.encode(url, "UTF-8") + "&w=$w&h=$h&fit=cover&output=webp&q=70"
    }
}

/** Poster in fester, kleiner Größe laden: schneller und passt immer in den Zwischenspeicher. */
private fun posterRequest(context: android.content.Context, url: String?, title: String? = null, series: Boolean = false, shrinkOnServer: Boolean = false): coil.request.ImageRequest =
    coil.request.ImageRequest.Builder(context)
        .data(if (url == null) "tmdb:" else CoverUrls.small(url, 200, 300, force = shrinkOnServer))
        .size(200, 300)
        .crossfade(false)
        // Fester Schlüssel: Kachel, Vorschau und Vorrat teilen sich dasselbe Bild im Arbeitsspeicher, und ein schon
        // geladenes Cover wird beim Zurückblättern sofort gezeigt (ohne den Umweg über den Hintergrund-Thread).
        .apply { val k = "p:" + (url ?: "tmdb:${title.orEmpty()}:$series"); memoryCacheKey(k); placeholderMemoryCacheKey(k) }
        // Titel für den TMDB-Ersatz (nicht Teil des Zwischenspeicher-Schlüssels)
        .apply { if (title != null) { setParameter("title", title, memoryCacheKey = null); setParameter("series", series, memoryCacheKey = null) } }
        .build()

/**
 * Vorschau-Bereich oben in Filme/Serien (Entwurf „Nachtblau – Filme“ / „Kino – Filme“):
 * Titel, Jahr, Bewertung, „Weiter ab …“. Nutzt nur das Poster, das schon im Zwischenspeicher liegt
 * (gleiche Anfrage wie die Kachel) – keine zusätzlichen Downloads, macht nichts langsamer.
 */
/**
 * Liest die Auswahl in einem eigenen Bereich: beim Wechsel des Posters wird NUR die Vorschau neu gezeichnet –
 * nicht die ganze Seite mit Kategorien und Raster (das machte das Blättern unnötig schwer).
 */
@Composable
private fun VodHeroSlot(
    app: AppController, api: VodProvider, hero: androidx.compose.runtime.State<VodItem?>, first: VodItem,
    series: Boolean, sourceId: String, category: String,
) {
    val item = hero.value ?: first
    // Inhaltsangabe: erst laden, wenn die Auswahl 0,6 s auf einem Film ruht (schnelles Blättern löst keine Anfragen aus),
    // danach im Speicher gemerkt – beim Zurückblättern und in den Details sofort da.
    val cacheKey = (if (series) "s:" else "m:") + sourceId + ":" + item.id
    var details by remember(cacheKey) { mutableStateOf(HeroInfo.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (details != null) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        val d = runCatching { if (series) api.seriesDetails(item.id).info else api.movieDetails(item.id) }.getOrNull()
        if (d != null) { HeroInfo.put(cacheKey, d); details = d }
    }
    VodHero(item = item, series = series, resumeMs = if (series) 0L else app.resumeOf("m:$sourceId:${item.id}"), category = category, details = details)
    Spacer(Modifier.height(12.dp))
}

/** Kleiner Speicher für Inhaltsangaben der Vorschau (höchstens 300 Einträge, älteste fliegen raus). */
internal object HeroInfo {
    private val map = object : LinkedHashMap<String, VodDetails>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VodDetails>?) = size > 300
    }
    @Synchronized fun get(k: String): VodDetails? = map[k]
    @Synchronized fun put(k: String, v: VodDetails) { map[k] = v }
}

@Composable
private fun VodHero(item: VodItem, series: Boolean, resumeMs: Long, category: String, details: VodDetails? = null) {
    val st = Palette.style
    val shape = RoundedCornerShape(Palette.barRadius)
    val ctx = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            // 150 dp war zu knapp: der Hinweis „OK = Details“ wurde unten abgeschnitten (Foto aus dem Fernseher-Test)
            .height(186.dp)
            .clip(shape)
            .background(Palette.Panel),
    ) {
        // Poster rechts als ruhiges Hintergrundbild, nach links ausgeblendet
        if (item.poster != null || de.aze.iptv.data.Tmdb.enabled) {
            AsyncImage(
                model = posterRequest(ctx, item.poster, item.name, series),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (st == de.aze.iptv.ui.AppStyle.KINO) 0.55f else 0.40f,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.45f),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to Palette.Panel, 0.5f to Palette.Panel.copy(alpha = 0.92f), 1f to Palette.Panel.copy(alpha = 0.15f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth(0.72f).padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                (if (resumeMs > 0) "WEITERSCHAUEN" else category.uppercase()),
                color = Palette.Highlight, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.name, color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                (details?.year ?: item.year)?.takeIf { it.isNotBlank() },
                details?.genre?.takeIf { it.isNotBlank() } ?: if (series) "Serie" else "Film",
                details?.duration?.takeIf { it.isNotBlank() && !series },
                item.rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ " + "%.1f".format(it) },
            ).joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (resumeMs > 0) {
                    Text(
                        "▶ Weiter ab ${fmtTime(resumeMs)}",
                        color = Palette.OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Palette.Accent, RoundedCornerShape(Palette.radius)).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Text(meta, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // kurze Inhaltsangabe – worum es geht (kommt vom Anbieter, sobald die Auswahl kurz ruht)
            val plot = details?.plot?.trim()?.takeIf { it.isNotEmpty() }
            Text(
                plot ?: Device.hint("OK = Details", "Tippen = Details"),
                color = if (plot != null) Palette.TextSoft else Palette.TextMuted,
                fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PosterCard(
    item: VodItem, number: Int, resumed: Boolean, series: Boolean = false, onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Focusable(modifier = modifier, onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, qaName = "V $number") { focused ->
        // Stil: Aze = Rahmen, Kino = wächst + weißer Rahmen, Glas = wächst + Schatten (nur Zeichnen)
        val st = Palette.style
        val pShape = RoundedCornerShape(st.posterRadius.dp)
        val ps by androidx.compose.animation.core.animateFloatAsState(if (focused) st.posterScale else 1f, androidx.compose.animation.core.tween(140), label = "ps")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .graphicsLayer {
                        scaleX = ps; scaleY = ps
                        if (focused && (st == de.aze.iptv.ui.AppStyle.GLAS || st == de.aze.iptv.ui.AppStyle.KLAR)) { shadowElevation = 16f; shape = pShape }
                    }
                    .clip(pShape)
                    .background(Palette.Raised)
                    .then(
                        if (!focused) Modifier
                        else when (st) {
                            de.aze.iptv.ui.AppStyle.AZE -> Modifier.border(3.dp, Palette.Accent, pShape)
                            de.aze.iptv.ui.AppStyle.KINO -> Modifier.border(2.dp, Color.White, pShape)
                            de.aze.iptv.ui.AppStyle.GLAS -> Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), pShape)
                            de.aze.iptv.ui.AppStyle.KLAR -> Modifier.border(3.dp, Color.White, pShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.name, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 4, modifier = Modifier.padding(8.dp))
                if (item.poster != null || de.aze.iptv.data.Tmdb.enabled) {
                    AsyncImage(
                        model = posterRequest(LocalContext.current, item.poster, item.name, series),
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
                fontSize = 12.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@kotlin.OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    var info by remember(item.id) { mutableStateOf<VodDetails?>(if (series) null else HeroInfo.get("m:$sourceId:${item.id}")) }
    var sd by remember(item.id) { mutableStateOf<SeriesDetails?>(null) }
    var season by remember(item.id) { mutableIntStateOf(-1) }
    var failed by remember(item.id) { mutableStateOf(false) }
    // IMDb-Bewertung (nur mit eigenem OMDb-Schlüssel in den Einstellungen)
    var imdb by remember(item.id) { mutableStateOf<de.aze.iptv.data.Omdb.Rating?>(null) }
    val playButton = remember { FocusRequester() }
    var attempt by remember(item.id) { mutableIntStateOf(0) }
    // Trailer (YouTube) im Vollbild über den Details
    var trailer by remember(item.id) { mutableStateOf<String?>(null) }
    val ref = remember(item.id, sourceId, series) { de.aze.iptv.data.VodRef.of(sourceId, series, item) }
    val watched = app.isWatched(sourceId, series, item.id)
    val dScope = rememberCoroutineScope()
    /** Markierung zurück auf den ersten Knopf, wenn der markierte verschwindet (Fernbedienung) */
    fun refocus() {
        dScope.launch {
            repeat(3) { withFrameNanos { } }
            repeat(6) { if (runCatching { playButton.requestFocus() }.isSuccess) return@launch; withFrameNanos { } }
        }
    }

    /** Folge abspielen – mit Eintrag für Weiterschauen und der nächsten Folge (Staffel für Staffel) */
    fun episodePlaying(e: Episode, start: Long, all: SeriesDetails?): Playing {
        val flat = all?.seasons?.values?.flatten().orEmpty()
        val at = flat.indexOfFirst { it.id == e.id }
        val nxt = if (at >= 0) flat.getOrNull(at + 1) else null
        val key = "e:$sourceId:${e.id}"
        return Playing(
            de.aze.iptv.data.Downloader.localUri(key) ?: api.episodeUrl(e), "${item.name} – S${e.season} E${e.number}", key, start,
            cont = de.aze.iptv.data.ContinueEntry(ref, key, de.aze.iptv.data.EpisodeRef.of(e)),
            next = nxt?.let { n -> { episodePlaying(n, app.resumeOf("e:$sourceId:${n.id}"), all) } },
            nextLabel = nxt?.let { n -> "S${n.season} E${n.number}" + n.title.takeIf { t -> t.isNotBlank() }?.let { t -> " · $t" }.orEmpty() },
        )
    }
    fun moviePlaying(start: Long): Playing {
        val key = "m:$sourceId:${item.id}"
        return Playing(de.aze.iptv.data.Downloader.localUri(key) ?: api.movieUrl(item), item.name, key, start, cont = de.aze.iptv.data.ContinueEntry(ref, key))
    }
    /** Trailer, Vormerken, aus Weiterschauen entfernen – bei Filmen und Serien gleich */
    val extraButtons: @Composable (inContinue: Boolean) -> Unit = { inContinue ->
        info?.trailer?.let { id ->
            TvButton("▶ Trailer", onClick = { qaLog("TRAILER $id"); trailer = id })
        }
        TvButton(if (watched) "✓ Vorgemerkt" else "Vormerken", selected = watched, onClick = { app.toggleWatch(ref) })
        if (inContinue) TvButton("Aus Weiterschauen entfernen", onClick = {
            app.removeContinue(sourceId, series, item.id)
            // der Knopf verschwindet – Fokus auf „Abspielen“ bzw. die erste Staffel (erst nach dem Neuaufbau)
            refocus()
        })
    }

    LaunchedEffect(item.id, attempt) {
        failed = false
        runCatching {
            if (series) {
                val s = api.seriesDetails(item.id)
                sd = s
                info = s.info
                season = s.seasons.keys.firstOrNull() ?: -1
            } else {
                info = api.movieDetails(item.id)
            }
        }.onFailure {
            // ohne Internet: geladene Folgen dieser Serie trotzdem zeigen (laufen vom Gerät)
            val off = if (series) offlineSeries(sourceId, item.id) else null
            if (off != null) {
                sd = off; info = off.info; season = off.seasons.keys.first()
                qaLog("VOD_OHNE_NETZ ${item.name} ${off.seasons.values.sumOf { it.size }} Folgen")
            } else failed = true
        }
        withFrameNanos { }
        runCatching { playButton.requestFocus() }
    }
    val i = info
    // Handy: wenig Höhe (quer) oder schmal (hoch) → kleineres Poster, Knöpfe gleich unter dem Titel, alles scrollbar
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val compact = cfg.screenHeightDp < 520 || cfg.screenWidthDp < 600
    val narrow = cfg.screenWidthDp < 600

    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        i?.backdrop?.let {
            AsyncImage(model = CoverUrls.small(it, 1280, 720), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Palette.Ground, Palette.Ground.copy(alpha = 0.92f), Palette.Ground.copy(alpha = 0.55f))),
            ),
        )
        Row(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 28.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 24.dp),
        ) {
            if (!narrow) {
                Box(Modifier.width(if (compact) 110.dp else 180.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Palette.Raised)) {
                    if (item.poster != null) AsyncImage(model = CoverUrls.small(item.poster, 400, 600), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            // Film: ganze Spalte scrollbar (Knöpfe nie abgeschnitten). Serie: Folgenliste scrollt selbst.
            val colMod = if (!series) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.weight(1f)
            Column(colMod, verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
                Text(item.name, color = Palette.Text, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                val movieButtons: @Composable () -> Unit = {
                    val key = "m:$sourceId:${item.id}"
                    val resume = app.resumeOf(key)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        if (resume > 0) {
                            TvButton("Weiterschauen ab ${fmtTime(resume)}", modifier = Modifier.focusRequester(playButton), onClick = {
                                onPlay(moviePlaying(resume))
                            })
                            TvButton("Von vorn", onClick = { onPlay(moviePlaying(0)) })
                        } else {
                            TvButton("Abspielen", modifier = Modifier.focusRequester(playButton), onClick = {
                                onPlay(moviePlaying(0))
                            })
                        }
                        extraButtons(resume > 0)
                        DownloadButtons(key, ref, null, onGone = { refocus() })
                        TvButton("Zurück", onClick = onBack)
                    }
                }
                if (!series && compact) movieButtons()
                // Serie lädt nicht: sonst wäre nichts anwählbar (Sackgasse mit der Fernbedienung)
                if (failed && series) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Nochmal versuchen", modifier = Modifier.focusRequester(playButton), onClick = { attempt++ })
                    TvButton("Zurück", onClick = onBack)
                }
                when {
                    failed && !series && de.aze.iptv.data.Downloader.localUri("m:$sourceId:${item.id}") != null ->
                        Text("Ohne Internet – der Film läuft vom Gerät.", color = Palette.TextSoft, fontSize = 14.sp)
                    failed -> Text("Details konnten nicht geladen werden.", color = Palette.Live, fontSize = 14.sp)
                    i == null -> Text("Lade …", color = Palette.TextMuted, fontSize = 14.sp)
                }
                i?.plot?.let { Text(it, color = Palette.Text, fontSize = if (compact) 14.sp else 15.sp, maxLines = if (compact && series) 3 else 6, overflow = TextOverflow.Ellipsis) }
                if (!(compact && series)) i?.director?.let { Text("Regie: $it", color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (!(compact && series)) i?.cast?.let { Text("Mit: $it", color = Palette.TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }

                if (!series) {
                    if (!compact) movieButtons()
                } else {
                    val s = sd
                    // zuletzt angefangene Folge dieser Serie (Weiterschauen-Reihe)
                    val ce = app.continueFor(sourceId, true).firstOrNull { it.ref.id == item.id }
                    val ceEp = ce?.episode
                    if (i != null) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (ceEp != null && ce != null) {
                                TvButton(
                                    "Weiterschauen S${ceEp.season} E${ceEp.number} ab ${fmtTime(app.resumeOf(ce.resumeKey))}",
                                    modifier = Modifier.focusRequester(playButton),
                                    onClick = { onPlay(episodePlaying(ceEp.toEpisode(), app.resumeOf(ce.resumeKey), s)) },
                                )
                            }
                            if (s != null && season in s.seasons) SeasonDownloadButton(sourceId, ref, season, s.seasons[season].orEmpty())
                            extraButtons(ce != null)
                        }
                    }
                    if (s != null && s.seasons.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.seasons.keys.toList()) { n ->
                                TvButton(
                                    "Staffel $n",
                                    selected = n == season,
                                    modifier = if (n == s.seasons.keys.first() && ceEp == null) Modifier.focusRequester(playButton) else Modifier,
                                    onClick = { season = n },
                                )
                            }
                        }
                        val eps = s.seasons[season].orEmpty()
                        Text(
                            Device.hint("OK lang auf einer Folge: nur diese herunterladen", "Folge lange antippen: nur diese herunterladen"),
                            color = Palette.TextMuted, fontSize = 12.sp,
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            items(eps.size, key = { i -> "$i|${eps[i].id}" }) { i ->
                                val e = eps[i]
                                EpisodeRow(app, e, sourceId, ref) { start ->
                                    onPlay(episodePlaying(e, start, s))
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
        // Trailer im Vollbild (YouTube); Zurück schließt ihn
        val t = trailer
        if (t != null) {
            BackHandler { trailer = null; refocus() }
            var trailerError by remember(t) { mutableStateOf<String?>(null) }
            // Markierung in den Trailer holen: sonst steuerte die Fernbedienung die verdeckten Knöpfe dahinter
            val trailerFocus = remember { FocusRequester() }
            LaunchedEffect(t) { withFrameNanos { }; runCatching { trailerFocus.requestFocus() } }
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .focusRequester(trailerFocus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if ((e.key == Key.Back || e.key == Key.Escape) && e.type == KeyEventType.KeyUp) { trailer = null; refocus() }
                        true
                    },
            ) {
                YouTubeView(
                    url = "youtube:video:$t",
                    modifier = Modifier.fillMaxSize(),
                    onPlaying = { trailerError = null },
                    onError = { trailerError = it },
                )
                Text(
                    trailerError ?: Device.hint("Trailer · Zurück schließt ihn", "Trailer · Zurück schließt ihn"),
                    color = if (trailerError != null) Palette.Live else Palette.TextSoft, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
                if (Device.isTouch) {
                    // etwas tiefer: oben rechts steht am Handy die Zahl „1 / 200“ (Wischen durch die Filme)
                    TvButton("✕ Schließen", onClick = { trailer = null; refocus() }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 12.dp))
                }
            }
        }
    }
}

/**
 * Wischen durch die Filme einer Kategorie (Handy/Tablet, Aziz 25.11.): nach oben = nächster Film mit Beschreibung,
 * nach unten = voriger, nach links oder rechts = zurück zur Kachel-Übersicht. Scrollt dabei etwas in der Ansicht (lange
 * Beschreibung, Folgenliste, Staffeln), ist das kein Wischen zum nächsten – erst wenn dort nichts mehr zu scrollen ist.
 */
@Composable
private fun SwipeDetail(
    enabled: Boolean,
    count: Int,
    index: Int,
    kind: String,
    onIndex: (Int) -> Unit,
    onBack: () -> Unit,
    content: @Composable (Int) -> Unit,
) {
    if (!enabled) { content(index); return }
    // hat während dieser Berührung etwas in der Ansicht gescrollt?
    val scrolled = remember { booleanArrayOf(false) }
    val conn = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (consumed.x != 0f || consumed.y != 0f) scrolled[0] = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val minPx = with(androidx.compose.ui.platform.LocalDensity.current) { 70.dp.toPx() }
    val current = androidx.compose.runtime.rememberUpdatedState(index)
    val toIndex = androidx.compose.runtime.rememberUpdatedState(onIndex)
    val toBack = androidx.compose.runtime.rememberUpdatedState(onBack)
    // „der nächste Film“ / „die nächste Serie“
    val film = kind == "Film"
    val art = if (film) "der" else "die"
    val next = if (film) "nächster" else "nächste"
    var edge by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(6_000); help = false }
    LaunchedEffect(edge) { if (edge != null) { delay(2_500); edge = null } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ground)
            .nestedScroll(conn)
            .pointerInput(count) {
                awaitEachGesture {
                    // nur mitlesen (nichts verbrauchen): Knöpfe und Scrollen darunter funktionieren normal
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    scrolled[0] = false
                    var pos = down.position
                    var multi = false
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (ev.changes.size > 1) multi = true
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        pos = c.position
                        if (!c.pressed) break
                    }
                    if (multi || scrolled[0]) return@awaitEachGesture
                    val dx = pos.x - down.position.x
                    val dy = pos.y - down.position.y
                    val i = current.value
                    when {
                        kotlin.math.abs(dy) >= minPx && kotlin.math.abs(dy) > 1.4f * kotlin.math.abs(dx) -> {
                            val n = if (dy < 0) i + 1 else i - 1
                            if (n in 0 until count) toIndex.value(n)
                            else edge = if (dy < 0) "Das war $art letzte $kind dieser Kategorie" else "Das ist $art erste $kind dieser Kategorie"
                        }
                        kotlin.math.abs(dx) >= minPx && kotlin.math.abs(dx) > 1.4f * kotlin.math.abs(dy) -> toBack.value()
                    }
                }
            },
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState > initialState
                (
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(280)) { h -> if (forward) h else -h } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(280))
                    ) togetherWith (
                    androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(280)) { h -> if (forward) -h / 3 else h / 3 } +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
                    )
            },
            label = "wischen",
        ) { i -> content(i) }
        // Wo man ist: 12 / 500
        Text(
            "${index + 1} / $count",
            color = Palette.TextSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                .background(Palette.Ground.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        val note = edge ?: if (help) "↑ wischen: $next $kind · ↓ zurück · ← → Übersicht" else null
        if (note != null) {
            Text(
                note,
                color = Palette.Text, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    .background(Palette.Ground.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRow(app: AppController, e: Episode, sourceId: String, ref: de.aze.iptv.data.VodRef, onPlay: (Long) -> Unit) {
    val key = "e:$sourceId:${e.id}"
    val resume = app.resumeOf(key)
    val dl = de.aze.iptv.data.Downloader.entries.firstOrNull { it.key == key }
    var askDelete by remember { mutableStateOf(false) }
    if (askDelete) ConfirmDialog(
        "Folge vom Gerät löschen?", "S${e.season} E${e.number} ${e.title}".trim() + " – läuft danach wieder nur mit Internet.", "Löschen",
        onYes = { de.aze.iptv.data.Downloader.delete(key); askDelete = false }, onNo = { askDelete = false },
    )
    // OK lang (bzw. lange antippen): nur diese Folge laden / anhalten / weiter / löschen
    val onLong: () -> Unit = {
        when (dl?.state) {
            null -> de.aze.iptv.data.Downloader.enqueue(ref, key, de.aze.iptv.data.EpisodeRef.of(e))
            de.aze.iptv.data.DlState.WAIT, de.aze.iptv.data.DlState.RUN -> de.aze.iptv.data.Downloader.pause(key)
            de.aze.iptv.data.DlState.PAUSE, de.aze.iptv.data.DlState.FAIL -> de.aze.iptv.data.Downloader.resume(key)
            de.aze.iptv.data.DlState.DONE -> { askDelete = true }
        }
        Unit
    }
    Focusable(modifier = Modifier.fillMaxWidth(), onClick = { onPlay(resume) }, onLongClick = onLong, qaName = "E ${e.season}.${e.number}") { focused ->
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
            if (dl != null) Text(dlShort(dl), color = if (focused) Palette.OnAccent else if (dl.state == de.aze.iptv.data.DlState.FAIL) Palette.Live else Palette.Highlight, fontSize = 12.sp)
            e.duration?.let { Text(it, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 12.sp) }
        }
    }
}

/** Kurz für Folgen-Zeilen und Knöpfe: „⬇ 45 %“, „✓ auf dem Gerät“ … */
private fun dlShort(d: de.aze.iptv.data.DlEntry): String = when (d.state) {
    de.aze.iptv.data.DlState.DONE -> "✓ auf dem Gerät"
    de.aze.iptv.data.DlState.RUN -> "⬇ " + (if (d.percent >= 0) "${d.percent} %" else de.aze.iptv.data.DownloadRules.size(d.done))
    de.aze.iptv.data.DlState.WAIT -> "⬇ wartet" + (if (d.percent > 0) " · ${d.percent} %" else "")
    de.aze.iptv.data.DlState.PAUSE -> "⏸ angehalten" + (if (d.percent >= 0) " · ${d.percent} %" else "")
    de.aze.iptv.data.DlState.FAIL -> "⚠ Download fehlgeschlagen"
}

/** Ohne Internet: geladene Folgen einer Serie als Staffeln (zum Abspielen vom Gerät) */
private fun offlineSeries(sourceId: String, seriesId: String): SeriesDetails? {
    val eps = de.aze.iptv.data.Downloader.entries.filter {
        it.ref.series && it.ref.sourceId == sourceId && it.ref.id == seriesId && it.state == de.aze.iptv.data.DlState.DONE && it.episode != null
    }.mapNotNull { it.episode?.toEpisode() }
    if (eps.isEmpty()) return null
    val seasons = eps.groupBy { it.season }.toSortedMap().mapValues { (_, l) -> l.sortedBy { it.number } }
    return SeriesDetails(VodDetails("Ohne Internet: nur die Folgen auf dem Gerät.", null, null, null, null, null, null, null), seasons)
}

/**
 * Knopf „Herunterladen“ in den Film-Details (Aziz 26.11.) – zeigt den Stand und bedient ihn:
 * laden → anhalten → weiter; fertig → löschen (mit Rückfrage). Dazu „Abbrechen“, solange es nicht fertig ist.
 */
@Composable
private fun DownloadButtons(key: String, ref: de.aze.iptv.data.VodRef, episode: de.aze.iptv.data.EpisodeRef?, onGone: () -> Unit) {
    val d = de.aze.iptv.data.Downloader.entries.firstOrNull { it.key == key }
    var ask by remember { mutableStateOf(false) }
    val label = when (d?.state) {
        null -> "⬇ Herunterladen"
        de.aze.iptv.data.DlState.DONE -> "✓ Auf dem Gerät · löschen"
        de.aze.iptv.data.DlState.RUN, de.aze.iptv.data.DlState.WAIT -> dlShort(d) + " · anhalten"
        de.aze.iptv.data.DlState.PAUSE -> dlShort(d) + " · weiter"
        de.aze.iptv.data.DlState.FAIL -> "⚠ ${d.error ?: "Fehler"} · nochmal"
    }
    TvButton(label, selected = d?.state == de.aze.iptv.data.DlState.DONE, modifier = Modifier.qaPos("DL KNOPF"), onClick = {
        when (d?.state) {
            null -> {
                de.aze.iptv.data.Downloader.enqueue(ref, key, episode)
            }
            de.aze.iptv.data.DlState.RUN, de.aze.iptv.data.DlState.WAIT -> de.aze.iptv.data.Downloader.pause(key)
            de.aze.iptv.data.DlState.PAUSE, de.aze.iptv.data.DlState.FAIL -> de.aze.iptv.data.Downloader.resume(key)
            de.aze.iptv.data.DlState.DONE -> { ask = true }
        }
    })
    if (d != null && d.state != de.aze.iptv.data.DlState.DONE) {
        TvButton("Download abbrechen", danger = true, onClick = { de.aze.iptv.data.Downloader.delete(key); onGone() })
    }
    if (ask) ConfirmDialog(
        "Vom Gerät löschen?", "„${d?.title ?: ref.name}“ läuft danach wieder nur mit Internet.", "Löschen",
        onYes = { de.aze.iptv.data.Downloader.delete(key); ask = false; onGone() }, onNo = { ask = false },
    )
}

/** Ganze Staffel laden (fehlende Folgen in die Warteschlange) bzw. Stand der Staffel */
@Composable
private fun SeasonDownloadButton(sourceId: String, ref: de.aze.iptv.data.VodRef, season: Int, eps: List<Episode>) {
    if (eps.isEmpty()) return
    val all = de.aze.iptv.data.Downloader.entries
    val keys = eps.map { "e:$sourceId:${it.id}" }
    val mine = all.filter { it.key in keys }
    val done = mine.count { it.state == de.aze.iptv.data.DlState.DONE }
    val busy = mine.any { it.state == de.aze.iptv.data.DlState.RUN || it.state == de.aze.iptv.data.DlState.WAIT }
    var ask by remember(season) { mutableStateOf(false) }
    val label = when {
        done == eps.size -> "✓ Staffel $season auf dem Gerät"
        busy -> "⬇ Staffel $season: $done von ${eps.size} · anhalten"
        mine.isNotEmpty() -> "⬇ Staffel $season: $done von ${eps.size} · weiter laden"
        else -> "⬇ Staffel $season herunterladen"
    }
    TvButton(label, selected = done == eps.size, modifier = Modifier.qaPos("DL STAFFEL"), onClick = {
        when {
            done == eps.size -> { ask = true }
            busy -> mine.filter { it.state == de.aze.iptv.data.DlState.RUN || it.state == de.aze.iptv.data.DlState.WAIT }
                .forEach { de.aze.iptv.data.Downloader.pause(it.key) }
            else -> eps.forEach { e ->
                val k = "e:$sourceId:${e.id}"
                val d = mine.firstOrNull { it.key == k }
                if (d == null) de.aze.iptv.data.Downloader.enqueue(ref, k, de.aze.iptv.data.EpisodeRef.of(e))
                else if (d.state != de.aze.iptv.data.DlState.DONE) de.aze.iptv.data.Downloader.resume(k)
            }
        }
    })
    if (ask) ConfirmDialog(
        "Staffel $season vom Gerät löschen?", "${eps.size} Folgen laufen danach wieder nur mit Internet.", "Löschen",
        onYes = { keys.forEach { de.aze.iptv.data.Downloader.delete(it) }; ask = false }, onNo = { ask = false },
    )
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
private fun VodPlayer(
    app: AppController,
    p: Playing,
    onExit: (posMs: Long, durMs: Long) -> Unit,
    onNext: (Playing) -> Unit = {},
) {
    val context = LocalContext.current
    // für Bild-im-Bild (Handy/Tablet): beim Verlassen der App weiter im kleinen Fenster
    androidx.compose.runtime.DisposableEffect(Unit) {
        app.vodPlaying = true
        // vom Anbieter (nicht vom Gerät): ein Download wartet bzw. bremst dann (Verbindungen des Anbieters)
        app.vodRemote = !p.url.startsWith("file:")
        if (!app.vodRemote) qaLog("VOD_VOM_GERAET ${p.title}")
        onDispose { app.vodPlaying = false; app.vodRemote = false }
    }
    val inPip = app.inPip
    // Filme/Serien: allgemeiner Ton-Bild-Versatz aus den Einstellungen
    val syncMs = app.audioDelay
    androidx.compose.runtime.SideEffect { de.aze.iptv.player.AudioSync.setMs(syncMs) }
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
    // Serie: „Nächste Folge in 10 Sekunden“ (in den letzten Sekunden oder am Ende der Folge)
    var nextHint by remember { mutableStateOf(false) }
    var readyLogged by remember { mutableStateOf(false) }
    var nextDismissed by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(10) }
    val nextFocus = remember { FocusRequester() }

    val player = remember {
        val http = DefaultHttpDataSource.Factory().setUserAgent(Http.USER_AGENT).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                (if (app.audioDelay != 0) de.aze.iptv.player.SyncRenderersFactory(context) else DefaultRenderersFactory(context))
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(!de.aze.iptv.player.FrameRate.isTv(context))
            // Speichergrenze wie beim Live-TV (sonst bis ~130 MB Vorrat – zu viel für schwache Fire TV Sticks)
            .setLoadControl(de.aze.iptv.player.PlaybackConfig.loadControl(context, de.aze.iptv.player.BufferLevel.LARGE))
            .build()
            .apply {
                videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                // gemerkte Sprachen (Aziz 25.11.): Tonspur und Untertitel wie beim letzten Film, wenn vorhanden
                val audio = app.vodAudioLang ?: app.audioLanguage
                val sub = app.vodSubLang
                trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
                    if (!audio.isNullOrBlank()) setPreferredAudioLanguage(audio)
                    when {
                        sub == "aus" -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        !sub.isNullOrBlank() -> setPreferredTextLanguage(sub)
                    }
                }.build()
            }
    }
    // Home-Taste / Bildschirmschoner: Film anhalten (läuft sonst hinter anderen Apps weiter)
    val vodLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(vodLifecycle, player) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.playWhenReady = false
        }
        vodLifecycle.lifecycle.addObserver(obs)
        onDispose { vodLifecycle.lifecycle.removeObserver(obs) }
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
                // verständlich statt Fehlercode (der steht klein dahinter, für Rückfragen)
                val why = when (e.errorCode) {
                    in 2000..2999 -> if (e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) "Der Anbieter lehnt den Film gerade ab."
                        else "Keine Verbindung zum Anbieter."
                    in 3000..3999 -> "Dieses Format kann das Gerät nicht abspielen."
                    in 4000..4999 -> "Bild oder Ton kann das Gerät nicht entschlüsseln."
                    else -> "Wiedergabe nicht möglich."
                }
                error = "$why\n(${e.errorCode})"
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && !readyLogged) {
                    readyLogged = true
                    qaLog("VOD_READY ${player.duration / 1000} s")
                }
                if (state == Player.STATE_ENDED) {
                    // Serie: nächste Folge ankündigen (außer der Hinweis wurde weggeklickt)
                    if (p.next != null && !nextDismissed) { ended = true; nextHint = true }
                    else scope.launch { onExit(0, 0) }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }
        }
        player.addListener(l)
        qaLog("VOD_PLAY ${p.title} ab ${p.startMs / 1000} s" + (p.nextLabel?.let { " · danach $it" } ?: ""))
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
            // letzte 10 Sekunden einer Folge: Hinweis auf die nächste schon jetzt
            if (p.next != null && !nextDismissed && !nextHint && duration > 60_000 && player.isPlaying &&
                duration - position in 1..10_000
            ) nextHint = true
            delay(500)
        }
    }
    // Countdown bis zur nächsten Folge (läuft nicht, solange angehalten)
    LaunchedEffect(nextHint) {
        if (!nextHint) return@LaunchedEffect
        qaLog("NAECHSTE_FOLGE ${p.nextLabel}")
        showUi = false
        picker = null
        var left = if (ended) 10_000L else (duration - player.currentPosition).coerceIn(1_000L, 10_000L)
        launch {
            repeat(12) {
                withFrameNanos { }
                if (runCatching { nextFocus.requestFocus() }.isSuccess) return@launch
            }
        }
        while (left > 0) {
            countdown = ((left + 999) / 1000).toInt()
            delay(200)
            if (ended || player.isPlaying) left -= 200
        }
        p.next?.invoke()?.let(onNext)
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
        runCatching { if (showUi && picker == null) playFocus.requestFocus() else if (!showUi && !nextHint) rootFocus.requestFocus() }
    }

    fun dismissNext() {
        qaLog("NAECHSTE_FOLGE_AB")
        nextHint = false
        nextDismissed = true
        if (ended) onExit(0, 0) else runCatching { rootFocus.requestFocus() }
    }

    BackHandler {
        when {
            // Zurück schließt nur den Hinweis (Aziz 25.11.); die Folge läuft zu Ende
            nextHint -> dismissNext()
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
                // Hinweis „Nächste Folge“: Zurück schließt ihn – hier abfangen, sonst nimmt Compose das erste Zurück
                // nur als „Markierung aus dem Knopf heraus“ und der Countdown läuft weiter
                if (nextHint && (e.key == Key.Back || e.key == Key.Escape)) {
                    if (e.type == KeyEventType.KeyUp) dismissNext()
                    return@onPreviewKeyEvent true
                }
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
                // Fehlermeldung offen: Tasten gehören den Knöpfen „Nochmal versuchen“ / „Zurück“
                if (error != null) return@onPreviewKeyEvent false
                // Hinweis „Nächste Folge“: Tasten gehören seinen Knöpfen
                if (nextHint) return@onPreviewKeyEvent false
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
            }
            // Tablet/Handy: Tippen zeigt/versteckt die Leiste, doppelt Tippen links/rechts spult 10 s
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (picker != null) picker = null else if (!nextHint) { showUi = !showUi; uiTick++ } },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2) { nudge(-1, 0); flash("−10 s") } else { nudge(+1, 0); flash("+10 s") }
                    },
                )
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

        // Bild-im-Bild: nur das Bild, keine Leisten oder Hinweise
        if (inPip) return@Box

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

        if ((showUi || target != null || !isPlaying) && !nextHint) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    // Handy quer: weniger Rand oben, sonst deckt die Leiste zwei Drittel des Bildes ab
                    .padding(start = 32.dp, end = 32.dp, top = if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480) 12.dp else 40.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!isPlaying && target == null) Text("Pause", color = Palette.Highlight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                // Zeitleiste: antippen oder ziehen springt an die Stelle (Tablet/Handy)
                Box(
                    Modifier.fillMaxWidth().height(28.dp)
                        .pointerInput(duration) {
                            if (duration <= 0) return@pointerInput
                            detectTapGestures { o ->
                                uiTick++
                                seekJob?.cancel(); pending = null
                                player.seekTo((duration * (o.x / size.width).coerceIn(0f, 1f)).toLong())
                            }
                        }
                        .pointerInput(duration) {
                            if (duration <= 0) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragEnd = { pending?.let { player.seekTo(it) }; pending = null },
                            ) { change, _ ->
                                uiTick++
                                seekJob?.cancel()
                                pending = (duration * (change.position.x / size.width).coerceIn(0f, 1f)).toLong()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ProgressBar(position = position, target = target, buffered = buffered, duration = duration)
                }
                Row {
                    Text(fmtTime(target ?: position), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (duration > 0) Text("−" + fmtTime(duration - (target ?: position)) + "  ·  " + fmtTime(duration), color = Palette.TextSoft, fontSize = 14.sp)
                }
                if (showUi) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                        // auf schmalen Bildschirmen (Tablet) seitlich wischbar statt abgeschnitten
                        modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
                        TvButton("✕", onClick = { exit() })
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
                        Device.hint("◀ ▶ spulen (halten = schneller) · OK Pause · 0–9 springt auf 0–90 %", "Leiste antippen oder ziehen · doppelt tippen = ±10 s"),
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
                        app.rememberVodSub("aus")
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
                                // Sprache merken – der nächste Film nimmt sie, wenn er sie hat
                                if (pk == C.TRACK_TYPE_AUDIO) app.rememberVodAudio(o.language) else app.rememberVodSub(o.language)
                                picker = null
                            },
                        )
                    }
                }
            }
        }

        error?.let {
            val retryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(Palette.radius)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(it, color = Palette.Text, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Nochmal versuchen", modifier = Modifier.focusRequester(retryFocus), onClick = {
                        error = null
                        player.prepare(); player.play()
                    })
                    TvButton("Zurück", onClick = { exit() })
                }
            }
            LaunchedEffect(it) { androidx.compose.runtime.withFrameNanos { }; runCatching { retryFocus.requestFocus() } }
        }

        // Serie: „Nächste Folge in 10 Sekunden“ – unten rechts, Zurück oder „Abbrechen“ schließt den Hinweis
        if (nextHint && error == null) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(28.dp).widthIn(max = 420.dp)
                    .background(Palette.Panel.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .border(1.dp, Palette.Highlight.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .qaPos("NAECHSTE FOLGE"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Nächste Folge in $countdown " + if (countdown == 1) "Sekunde" else "Sekunden",
                    color = Palette.Highlight, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
                p.nextLabel?.let { Text(it, color = Palette.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("▶ Jetzt ansehen", modifier = Modifier.focusRequester(nextFocus), onClick = {
                        nextHint = false
                        p.next?.invoke()?.let(onNext)
                    })
                    TvButton("Abbrechen", onClick = { dismissNext() })
                }
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

/**
 * Sprache einer Kategorie für das Vorladen: „DE• Action“, „DE | …“, „GER …“, „Deutsch …“ = de;
 * „TR …“ oder türkische Wörter/Buchstaben („KONSERLER“, „TIYATRO“, „… FILMLERI“, „Yerli“, „Dizi“) = tr.
 */
private val deCat = Regex("(?i)^\\W*(de|ger|deu)\\b|deutsch")
private val trCat = Regex("(?i)^\\W*tr\\b|türk|turk|yerli|dizi|filmleri|konser|tiyatro|sinema")
// türkische Sonderbuchstaben – bewusst OHNE (?i), sonst passt „İ“ auch auf jedes „i“
private val trChars = Regex("[ıİşŞğĞ]")
internal fun vodLanguage(name: String): String? = when {
    deCat.containsMatchIn(name) -> "de"
    trCat.containsMatchIn(name) || trChars.containsMatchIn(name) -> "tr"
    else -> null
}



private fun stockKey(it: VodItem) = it.poster ?: ("tmdb:" + it.name)
/** schon versucht (auch erfolglos) – nicht endlos wiederholen; wird je Runde geleert */
private val stockTried = java.util.Collections.synchronizedSet(HashSet<String>())
private fun stockWanted(it: VodItem) =
    (it.poster != null || de.aze.iptv.data.Tmdb.enabled) && stockKey(it) !in stockTried && !de.aze.iptv.data.CoverStore.has(stockKey(it))

private val stockHttp by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

/** Bild holen, höchstens 3 MB (sonst auslassen) */
private fun stockBytes(url: String): ByteArray? = runCatching {
    stockHttp.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) return null
        val input = r.body?.byteStream() ?: return null
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf); if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > 3_000_000) return null
        }
        out.toByteArray()
    }
}.getOrNull()

/** ein Cover für den Vorrat: verkleinert vom Bilderdienst, sonst Original vom Anbieter, sonst TMDB */
private suspend fun stockOne(item: VodItem, series: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val key = stockKey(item)
    item.poster?.let { p ->
        val small = CoverUrls.small(p, 200, 300, force = true)
        for (u in if (small != p) listOf(small, p) else listOf(p)) {
            val b = stockBytes(u) ?: continue
            if (de.aze.iptv.data.CoverStore.saveFrom(b, key)) return@withContext
        }
    }
    if (de.aze.iptv.data.Tmdb.enabled) de.aze.iptv.data.Tmdb.poster(item.name, series)?.let { u ->
        stockBytes(u)?.let { de.aze.iptv.data.CoverStore.saveFrom(it, key) }
    }
}

/**
 * 2 Cover gleichzeitig holen – ohne die Bildbibliothek (kein Zwischenspeicher, kein großes Bild im Speicher).
 * Direkt nach dem Umschalten 15 s Pause, damit der neue Sender in Ruhe anläuft.
 */
private suspend fun stockFetch(@Suppress("UNUSED_PARAMETER") loader: Any?, @Suppress("UNUSED_PARAMETER") ctx: android.content.Context, items: List<VodItem>, series: Boolean) {
    items.forEach { stockTried.add(stockKey(it)) }
    while (System.currentTimeMillis() - CoverUrls.lastZap < 15_000) delay(1_000)
    kotlinx.coroutines.coroutineScope {
        items.chunked(2).forEach { pair ->
            // auch mitten im Block: nach einem Senderwechsel erst wieder 15 s Ruhe für den neuen Sender
            while (System.currentTimeMillis() - CoverUrls.lastZap < 15_000) delay(1_000)
            pair.map { item -> async { runCatching { stockOne(item, series) } } }.forEach { it.await() }
        }
    }
}

/** Filme & Serien offen: Cover der offenen Kategorie ab der sichtbaren Stelle holen; true = es gab etwas zu tun */
private suspend fun stockFocus(loader: Any?, ctx: android.content.Context): Boolean {
    val list = CoverUrls.focusList ?: return false
    // die sichtbaren (~12) lädt das Raster selbst – der Vorrat macht direkt dahinter weiter
    val from = (CoverUrls.focusFrom + 12).coerceIn(0, list.size)
    val next = (list.subList(from, list.size).asSequence() + list.subList(0, from).asSequence())
        .filter { stockWanted(it) }.take(4).toList()
    if (next.isEmpty()) return false
    stockFetch(loader, ctx, next, CoverUrls.focusSeries)
    return true
}

/**
 * Cover-Vorrat im Hintergrund auffüllen: alle Cover (bevorzugte Sprache zuerst, ohne ausgeblendete Kategorien)
 * einmal holen und klein speichern. Sind Filme & Serien offen, kommt zuerst die gerade offene Kategorie dran –
 * so hilft der Vorrat auch direkt nach der Installation.
 */
internal suspend fun runCoverStock(app: AppController, ctx: android.content.Context) {
    val loader: Any? = null
    // erst nach dem Start (Sender soll in Ruhe anlaufen) – außer Filme werden gleich geöffnet
    for (i in 0 until 20) { if (CoverUrls.vodOpen) break; delay(1_000) }
    while (true) {
        var fetched = 0
        stockTried.clear()
        for (src in app.vodSources) for (series in listOf(false, true)) {
            val api = runCatching { app.vodProvider(src) }.getOrNull() ?: continue
            val cats = runCatching { if (series) api.seriesCategories() else api.movieCategories() }.getOrNull() ?: continue
            val all = runCatching { api.all(series) }.getOrNull() ?: continue
            val skip = cats.filter {
                app.isVodHidden(src.id, series, it.id) || (app.adultFilter && de.aze.iptv.data.AdultFilter.isAdult(it.name))
            }.map { it.id }.toSet()
            val langOf = cats.associate { it.id to vodLanguage(it.name) }
            val order = if (app.vodLang == 1) listOf("tr", "de") else listOf("de", "tr")
            val visible = all.filter { it.cat !in skip }
            val queue = (order.flatMap { l -> visible.filter { langOf[it.cat] == l } } + visible.filter { langOf[it.cat] !in order })
                .filter { stockWanted(it) }
            var i = 0
            while (i < queue.size) {
                if (de.aze.iptv.data.CoverStore.full) return
                // Filme & Serien offen: erst die offene Kategorie; ist die fertig, kurz warten (nichts anderes bremst dann)
                if (CoverUrls.vodOpen) {
                    if (stockFocus(loader, ctx)) { fetched += 4; continue }
                    delay(2_000); continue
                }
                val chunk = queue.subList(i, minOf(i + 4, queue.size)).filter { stockWanted(it) }
                i += 4
                if (chunk.isEmpty()) continue
                stockFetch(loader, ctx, chunk, series)
                fetched += chunk.size
            }
        }
        qaLog("COVER_STOCK Runde fertig: $fetched neu, ${de.aze.iptv.data.CoverStore.count} im Vorrat")
        // fertig: alle 6 Stunden nach neuen Filmen schauen – offene Kategorien aber weiter sofort bedienen
        val until = System.currentTimeMillis() + 6 * 60 * 60_000L
        while (System.currentTimeMillis() < until) {
            if (!(CoverUrls.vodOpen && stockFocus(loader, ctx))) delay(2_000)
        }
    }
}


/** Erklär-Video aus den Einstellungen abspielen (gleicher Player wie bei Filmen) */
@Composable
internal fun HelpVideoPlayer(app: AppController, url: String, title: String, onExit: () -> Unit) {
    VodPlayer(app, Playing(url, title, "hilfe:$title", 0L), onExit = { _, _ -> onExit() })
}
