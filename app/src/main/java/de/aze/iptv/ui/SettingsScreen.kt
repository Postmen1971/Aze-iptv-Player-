package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.aze.iptv.AppController
import de.aze.iptv.BuildConfig
import de.aze.iptv.data.Network
import de.aze.iptv.data.PlaylistSource
import de.aze.iptv.data.ProxySettings
import de.aze.iptv.data.ProxyType
import de.aze.iptv.data.PhoneInputServer
import de.aze.iptv.data.WebEditServer
import de.aze.iptv.data.Secure
import androidx.compose.runtime.DisposableEffect
import de.aze.iptv.player.AspectMode
import de.aze.iptv.player.BufferLevel
import kotlinx.coroutines.delay
import de.aze.iptv.data.SourceType
import de.aze.iptv.update.Release
import de.aze.iptv.update.Updater
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val intervals = listOf(0 to "Nur manuell", 24 to "24 Std.", 48 to "48 Std.", 72 to "72 Std.", 336 to "2 Wochen")

@Composable
fun SettingsScreen(app: AppController, onClose: () -> Unit) {
    var editingList by remember { mutableStateOf(false) }
    if (editingList) {
        ListEditor(app, onClose = { editingList = false })
        return
    }

    // PIN ändern: 1 = neue PIN eingeben, 2 = wiederholen
    var pinStep by remember { mutableStateOf(0) }
    var newPin by remember { mutableStateOf("") }
    if (pinStep > 0) {
        PinScreen(
            title = if (pinStep == 1) "Neue PIN eingeben" else "Neue PIN wiederholen",
            onEntered = { pin ->
                if (pinStep == 1) {
                    newPin = pin
                    pinStep = 2
                    true
                } else if (pin == newPin) {
                    app.changePin(pin)
                    app.message = "PIN geändert."
                    pinStep = 0
                    true
                } else {
                    false
                }
            },
            onCancel = { pinStep = 0 },
        )
        return
    }

    BackHandler(onBack = onClose)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    var cat by remember { mutableStateOf(0) }
    // Standard: das Wichtigste. „Erweitert“ blendet Technik (Netzwerk, Puffer, Player …) zusätzlich ein.
    val cats = listOfNotNull(
        "Playlists", "YouTube Live", "Senderliste", "Bedienung", "Wiedergabe", "Programm (EPG)",
        if (app.expertMode) "Netzwerk" else null, "Sicherheit", "Autostart", "App-Update", "Über",
    )

    // Kompakt: links die Bereiche, rechts nur der gewählte Bereich.
    Row(modifier = Modifier.fillMaxSize().background(Palette.ScreenBrush)) {
        Column(
            modifier = Modifier
                .width(210.dp)
                .fillMaxHeight()
                .background(Palette.PanelBrush)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Einstellungen", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            CategoryItem("← Fernsehen", selected = false, modifier = Modifier.focusRequester(firstFocus), onFocus = {}, onClick = onClose)
            cats.forEachIndexed { i, name ->
                CategoryItem(name, selected = i == cat, onFocus = { cat = i }, onClick = { cat = i })
            }
            Spacer(Modifier.weight(1f))
            CategoryItem(
                if (app.expertMode) "Erweitert: an" else "Erweitert: aus",
                selected = app.expertMode, onFocus = {},
                onClick = { app.updateExpertMode(!app.expertMode); cat = 0 },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            app.message?.let {
                Text(it, color = Palette.TextSoft, fontSize = 13.sp)
            }
            when (cats.getOrNull(cat)) {
                "Playlists" -> {
                    PhoneInputSection(app)
                    PlaylistSection(app)
                    FreeListSection(app)
                }
                "YouTube Live" -> YouTubeSection(app)
                "Senderliste" -> {
                    RefreshSection(app)
                    PlaybackSection(app, onEditList = { editingList = true }, part = 1)
                    FavoritesSection(app)
                }
                "Bedienung" -> ControlsSection(app)
                "Wiedergabe" -> PlaybackSection(app, onEditList = { editingList = true }, part = 2)
                "Programm (EPG)" -> EpgSection(app)
                "Netzwerk" -> NetworkSection(app)
                "Sicherheit" -> Section("Sicherheit") {
                    Text(
                        if (app.pinIsDefault) "Die PIN ist noch 0000. Bitte eine eigene PIN festlegen."
                        else "Die Einstellungen sind mit deiner PIN geschützt.",
                        color = if (app.pinIsDefault) Palette.Accent else Palette.TextSoft,
                        fontSize = 14.sp,
                    )
                    Text(
                        "Zugangsdaten werden verschlüsselt gespeichert, Links nur gekürzt angezeigt, " +
                            "und die App macht keine Sicherungen in die Cloud oder auf andere Geräte.",
                        color = Palette.TextMuted, fontSize = 13.sp,
                    )
                    Text("Erwachsenen-Filter", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(if (app.adultFilter) "✓ An" else "An", selected = app.adultFilter, onClick = { app.updateAdultFilter(true) })
                        TvButton(if (!app.adultFilter) "✓ Aus" else "Aus", selected = !app.adultFilter, onClick = { app.updateAdultFilter(false) })
                    }
                    Text(
                        "Blendet Gruppen, Sender, Filme und Serien mit Erwachsenen-Inhalten aus (erkannt an Namen wie XXX, Adult, 18+, Erotik, Yetişkin).",
                        color = Palette.TextMuted, fontSize = 13.sp,
                    )
                    Text("PIN-Abfrage", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(if (app.pinEnabled) "✓ An" else "An", selected = app.pinEnabled, onClick = { app.updatePinEnabled(true) })
                        TvButton(if (!app.pinEnabled) "✓ Aus" else "Aus", selected = !app.pinEnabled, onClick = { app.updatePinEnabled(false) })
                    }
                    Text(
                        if (app.pinEnabled) "Vor den Einstellungen wird die PIN abgefragt."
                        else "Die Einstellungen öffnen sich ohne PIN – jeder mit der Fernbedienung kommt hinein.",
                        color = Palette.TextMuted, fontSize = 13.sp,
                    )
                    TvButton("PIN ändern", onClick = { pinStep = 1 })
                }
                "Autostart" -> AutostartSection(app)
                "App-Update" -> UpdateSection()
                else -> AboutSection(app)
            }
        }
    }
}

@Composable
private fun CategoryItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Focusable(modifier = modifier.fillMaxWidth(), onClick = onClick, onFocus = onFocus) { focused ->
        Text(
            label,
            color = if (focused) Palette.OnAccent else if (selected) Palette.Accent else Palette.Text,
            fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (focused) Palette.Accent else Palette.PanelDark.copy(alpha = 0f), RoundedCornerShape(7.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Section(title: String, startOpen: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    // Einklappbar: Überschrift antippen bzw. OK
    var open by remember(title) { mutableStateOf(startOpen) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.PanelBrush, RoundedCornerShape(14.dp))
            .border(1.dp, Palette.Line.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Focusable(modifier = Modifier.fillMaxWidth(), onClick = { open = !open }) { focused ->
            Row(
                Modifier.fillMaxWidth()
                    .then(if (focused) Modifier.background(Palette.FocusBrush, RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = if (focused) Palette.OnAccent else Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (open) "▾" else "▸", color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 18.sp)
            }
        }
        if (open) content()
    }
}

/** Bedienung: Zappen, Sendernamen, Tastenbelegung. */
@Composable
private fun ControlsSection(app: AppController) {
    Section("Zappen") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Alle Sender", selected = !app.zapInGroup, onClick = { app.updateZapInGroup(false) })
            TvButton("Nur in der Gruppe", selected = app.zapInGroup, onClick = { app.updateZapInGroup(true) })
        }
        Text(
            "„Nur in der Gruppe“: Hoch/Runter bleibt in der Gruppe des laufenden Senders (am Ende geht es von vorn los).",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
    }
    Section("Sendername beim Umschalten") {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Nur bei schwarzem Bild", selected = app.nameDisplay == 0, onClick = { app.updateNameDisplay(0) })
            TvButton("3 Sekunden mit Sendung", selected = app.nameDisplay == 1, onClick = { app.updateNameDisplay(1) })
            TvButton("Aus", selected = app.nameDisplay == 2, onClick = { app.updateNameDisplay(2) })
        }
    }
    Section("Tastenbelegung der Fernbedienung") {
        Text(
            "OK auf einer Taste wählt die nächste Aktion. Zifferntasten und Zurück bleiben fest.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        de.aze.iptv.data.RemoteKey.entries.forEach { k ->
            val a = app.keyAction(k)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(k.label, color = Palette.TextSoft, fontSize = 14.sp, modifier = Modifier.width(150.dp))
                TvButton(a.label + if (a == k.default) "" else "  ✎", onClick = {
                    val all = de.aze.iptv.data.KeyAction.entries
                    app.setKeyAction(k, all[(a.ordinal + 1) % all.size])
                })
            }
        }
        TvButton("Alle Tasten zurücksetzen", onClick = { app.resetKeyMap() })
    }
}

/** Freie Sender (iptv-org) per Knopfdruck dazunehmen – jeweils als eigene Playlist und eigene Gruppe. */
@Composable
private fun FreeListSection(app: AppController) {
    Section("Freie Sender (iptv-org)") {
        Text(
            "Öffentlich frei empfangbare Sender aus der Sammlung iptv-org. Die Liste wird aus dem Internet geladen und " +
                "mit „Jetzt aktualisieren“ auf den neuesten Stand gebracht. Jede Liste erscheint als eigene Gruppe. " +
                "Manche Streams sind nur im jeweiligen Land abrufbar oder zeitweise offline.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            de.aze.iptv.data.FreeLists.entries.forEach { e ->
                val on = app.sources.any { it.url.trim() == de.aze.iptv.data.FreeLists.url(e.code) }
                TvButton(if (on) "✓ ${e.name}" else "+ ${e.name}", selected = on, onClick = { app.toggleFreeList(e) })
            }
        }
    }
}

@Composable
private fun PlaylistSection(app: AppController) {
    Section("Playlists") {
        if (app.sources.isEmpty()) {
            Text("Noch keine Playlist. Füge unten einen M3U-Link oder Server + Login hinzu.", color = Palette.TextMuted, fontSize = 14.sp)
        }
        if (app.sources.size > 1) {
            Text(
                "Alle Playlists erscheinen in der Senderliste untereinander, in dieser Reihenfolge. " +
                    "Oben in der Senderliste kannst du auch nur eine Playlist anzeigen lassen.",
                color = Palette.TextMuted, fontSize = 13.sp,
            )
        }
        app.sources.forEachIndexed { i, src ->
            var renaming by remember(src.id) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (src.enabled) src.name else "${src.name} (pausiert)",
                        color = if (src.enabled) Palette.Text else Palette.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when (src.type) {
                            SourceType.M3U -> "M3U-Link · "
                            SourceType.XTREAM -> "Server + Login · "
                            SourceType.STALKER -> "Portal + MAC · "
                        } + Secure.shortUrl(src.url),
                        color = Palette.TextMuted,
                        fontSize = 14.sp,
                    )
                    app.sourceStatus[src.id]?.let { st ->
                        Text(st, color = if (st.startsWith("Fehler") || st.contains("keine")) Palette.Live else Palette.Accent, fontSize = 14.sp)
                    }
                }
                if (app.sources.size > 1) {
                    if (i > 0) TvButton("▲", onClick = { app.moveSource(src, -1) })
                    if (i < app.sources.size - 1) TvButton("▼", onClick = { app.moveSource(src, +1) })
                }
                // Pausieren: Liste wird nicht geladen, bleibt aber gespeichert und lässt sich jederzeit wieder einschalten
                TvButton(if (src.enabled) "Pausieren" else "Einschalten", selected = !src.enabled, onClick = { app.setSourceEnabled(src, !src.enabled) })
                TvButton("Umbenennen", onClick = { renaming = true })
                TvButton("Löschen", danger = true, onClick = { app.removeSource(src) })
            }
            if (renaming) {
                InputDialog("Name der Playlist", src.name, password = false, keyboard = KeyboardType.Text, onDone = {
                    app.renameSource(src, it)
                    renaming = false
                }, onCancel = { renaming = false })
            }
        }

        var mode by remember { mutableStateOf(SourceType.M3U) }
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }

        Text("Playlist hinzufügen", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("M3U-Link", selected = mode == SourceType.M3U, onClick = { mode = SourceType.M3U })
            TvButton("Server + Login", selected = mode == SourceType.XTREAM, onClick = {
                if (mode == SourceType.STALKER) user = ""
                mode = SourceType.XTREAM
            })
            TvButton("Portal + MAC", selected = mode == SourceType.STALKER, onClick = {
                mode = SourceType.STALKER
                if (user.isBlank()) user = "00:1A:79:"
            })
        }
        InputRow("Name", name, placeholder = "z. B. Mein Anbieter") { name = it }
        if (mode == SourceType.M3U) {
            InputRow("M3U-Link (komplett)", url, placeholder = "http://…", keyboard = KeyboardType.Uri) { url = it }
        } else if (mode == SourceType.STALKER) {
            InputRow("Portal-Adresse", url, placeholder = "http://server:port/c/", keyboard = KeyboardType.Uri) { url = it }
            InputRow("MAC-Adresse", user, placeholder = "00:1A:79:XX:XX:XX") { user = it.uppercase() }
        } else {
            InputRow("Server-Adresse", url, placeholder = "http://server:port", keyboard = KeyboardType.Uri) { url = it }
            InputRow("Benutzer", user) { user = it }
            InputRow("Passwort", pass, password = true) { pass = it }
        }
        val macOk = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(user.trim().uppercase())
        val valid = url.isNotBlank() && when (mode) {
            SourceType.M3U -> true
            SourceType.XTREAM -> user.isNotBlank() && pass.isNotBlank()
            SourceType.STALKER -> macOk
        }
        TvButton(if (valid) "Speichern und laden" else "Speichern und laden (Angaben fehlen)", onClick = {
            if (!valid) return@TvButton
            app.addSource(
                PlaylistSource(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { if (mode == SourceType.M3U) "Playlist" else if (mode == SourceType.STALKER) "Portal" else "Anbieter" },
                    type = mode,
                    url = url.trim(),
                    user = user.trim(),
                    pass = pass,
                )
            )
            name = ""; url = ""; user = ""; pass = ""
        })
    }
}

@Composable
private fun RefreshSection(app: AppController) {
    Section("Senderliste aktualisieren") {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            intervals.forEach { (hours, label) ->
                TvButton(label, selected = app.refreshHours == hours, onClick = { app.updateRefreshHours(hours) })
            }
        }
        val last = if (app.lastRefresh > 0) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(app.lastRefresh)) else "noch nie"
        Text("Zuletzt aktualisiert: $last", color = Palette.TextMuted, fontSize = 13.sp)
        TvButton(if (app.loading) "Wird aktualisiert …" else "Jetzt aktualisieren", onClick = { app.refreshNow() })
    }
}

/** Öffentliche EPG-Quellen zum Antippen (XMLTV, gz) */
private val epgPresets = listOf(
    "Deutschland (epgshare01)" to "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz",
    "Türkei (epgshare01)" to "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz",
)

@Composable
private fun EpgSection(app: AppController) {
    var adding by remember { mutableStateOf(false) }
    Section("Programmzeitschrift (EPG)") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("EPG an", selected = app.epgEnabled, onClick = { app.updateEpgEnabled(true) })
            TvButton("EPG aus", selected = !app.epgEnabled, onClick = { app.updateEpgEnabled(false) })
        }
        Text(
            if (app.epgEnabled)
                "EPG an: Unter den Sendern steht, was gerade läuft. Dafür lädt die App alle paar Stunden die Programmdaten " +
                    "(oft über 100 MB) und rechnet sie im Hintergrund um – auf schwächeren Geräten kann das kurz bremsen."
            else
                "EPG aus: Keine Programmdaten – die App lädt und rechnet nichts im Hintergrund, startet schneller und läuft am flüssigsten. " +
                    "Empfohlen, wenn du das laufende Programm nicht brauchst.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        Text(
            "Das Programm wird automatisch zugeordnet: zuerst über tvg-id aus der Senderliste, dann über tvg-name und den Sendernamen " +
                "(„DE: Das Erste HD“ passt auch zu „Das Erste“). Fehlt ein Programm, im Bild OK lang → „Programm (EPG)“ den Sender von Hand zuordnen.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        val own = remember(app.sources) { app.epgFromSources() }
        Text("Vom Anbieter / aus der Playlist", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (own.isEmpty()) Text("Keine – die Playlist enthält keine EPG-Adresse (url-tvg).", color = Palette.TextMuted, fontSize = 13.sp)
        own.forEach { EpgSourceRow(app, it, removable = false) }
    }
    Section("Weitere EPG-Quellen") {
        Text(
            "Für Listen ohne oder mit lückenhaftem Programm: XMLTV-Adressen anderer Anbieter oder öffentliche Listen hinzufügen (.xml oder .xml.gz). " +
                "Alle Quellen werden zusammengeführt.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        app.epgExtra.forEach { u -> EpgSourceRow(app, u, removable = true) }
        Text(
            "Zeiten falsch (z. B. türkisches Programm 1–2 Stunden versetzt)? Bei der Quelle mit „− 1 h“ / „+ 1 h“ korrigieren. " +
                "Zeitangaben mit Zeitzone in der Datei rechnet die App automatisch in deine Uhrzeit um.",
            color = Palette.TextMuted, fontSize = 12.sp,
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("+ Eigene Adresse", onClick = { adding = true })
            epgPresets.filter { it.second !in app.epgExtra }.forEach { (label, url) ->
                TvButton("+ $label", onClick = { app.addEpgUrl(url) })
            }
        }
    }
    Section("EPG-Status") {
        val withEpg = remember(app.epg, app.lineup) { app.lineup.all.count { app.epg.idFor(it.channel) != null } }
        Text(
            if (app.epg.channelCount > 0) "$withEpg von ${app.lineup.all.size} Sendern haben Programm · eigene Zuordnungen: ${app.epgMap.size}"
            else "Noch keine Programmdaten geladen",
            color = Palette.TextSoft, fontSize = 13.sp,
        )
        if (app.epgReport.isNotEmpty()) Text(app.epgReport, color = Palette.TextMuted, fontSize = 13.sp)
        TvButton(if (app.epgLoading) "Wird geladen …" else "Programm jetzt laden", onClick = { app.refreshEpg() })
    }
    if (adding) {
        InputDialog(
            label = "EPG-Adresse (XMLTV, auch .gz)",
            initial = "",
            password = false,
            keyboard = KeyboardType.Uri,
            onDone = {
                adding = false
                app.addEpgUrl(it)
            },
            onCancel = { adding = false },
        )
    }
}

@Composable
private fun EpgSourceRow(app: AppController, url: String, removable: Boolean) {
    val h = app.epgShifts[url] ?: 0
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(shortUrl(url), color = Palette.Text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.widthIn(max = 420.dp))
        Text(if (h == 0) "Zeit ±0 h" else "Zeit ${if (h > 0) "+" else ""}$h h", color = if (h == 0) Palette.TextMuted else Palette.Highlight, fontSize = 13.sp)
        TvButton("− 1 h", onClick = { app.shiftEpg(url, h - 1) })
        TvButton("+ 1 h", onClick = { app.shiftEpg(url, h + 1) })
        if (removable) TvButton("Entfernen", danger = true, onClick = { app.removeEpgUrl(url) })
    }
}

/** Adresse ohne Login-Daten anzeigen */
private fun shortUrl(u: String): String =
    u.substringBefore('?').let { if (u.contains("password=")) "$it (mit Login)" else it }

@Composable
private fun AutostartSection(app: AppController) {
    val context = LocalContext.current
    Section("Autostart") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Aus", selected = !app.autostart, onClick = { app.updateAutostart(false) })
            TvButton("Beim Einschalten starten", selected = app.autostart, onClick = { app.updateAutostart(true) })
        }
        if (app.autostart && android.os.Build.VERSION.SDK_INT >= 29 && !android.provider.Settings.canDrawOverlays(context)) {
            Text(
                "Dieses Gerät braucht dafür die Erlaubnis „Über anderen Apps anzeigen“. Ohne sie startet die App nach dem Einschalten nicht von selbst.",
                color = Palette.Accent, fontSize = 13.sp,
            )
            TvButton("Erlaubnis öffnen", onClick = {
                val i = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                val ok = runCatching { context.startActivity(i) }.isSuccess
                if (!ok) app.message = "Dieses Gerät bietet die Einstellung nicht an. Autostart klappt hier evtl. nicht."
            })
        }
    }
}

@Composable
private fun PhoneInputSection(app: AppController) {
    var server by remember { mutableStateOf<PhoneInputServer?>(null) }
    var address by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    Section("Playlist vom Handy eingeben") {
        val s = server
        if (s == null) {
            Text(
                "Handy und Fernseher müssen im selben WLAN sein. Die Eingabe ist nur 5 Minuten offen und braucht einen Code.",
                color = Palette.TextMuted, fontSize = 13.sp,
            )
            TvButton("Handy-Eingabe starten", onClick = {
                val ip = PhoneInputServer.localAddress()
                if (ip == null) {
                    app.message = "Keine WLAN-/LAN-Adresse gefunden. Ist das Gerät mit dem Heimnetz verbunden?"
                    return@TvButton
                }
                val srv = PhoneInputServer(
                    onSource = { src ->
                        app.addSource(src)
                        app.message = "Playlist „${src.name}“ vom Handy übernommen."
                    },
                    onYoutube = { link, name -> app.addYoutube(link, name) },
                    onStopped = { server = null },
                )
                if (srv.start()) {
                    address = "http://$ip:${srv.port}"
                    server = srv
                } else {
                    app.message = "Handy-Eingabe konnte nicht gestartet werden."
                }
            })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                // QR-Code: mit der Handy-Kamera scannen – öffnet die Seite mit dem Code schon eingetragen
                QrCode("${address ?: ""}/?c=${s.code}")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QR-Code mit der Handy-Kamera scannen – oder im Browser öffnen:", color = Palette.TextSoft, fontSize = 14.sp)
                    Text(address ?: "", color = Palette.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Code:", color = Palette.TextSoft, fontSize = 18.sp)
                        Text(s.code, color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Schließt sich nach der Eingabe oder nach 5 Minuten von selbst.", color = Palette.TextMuted, fontSize = 13.sp)
            TvButton("Beenden", onClick = { s.stop() })
        }
    }
}

/** Senderliste am Handy oder PC bearbeiten (Ziehen zum Sortieren). */
@Composable
private fun WebEditSection(app: AppController) {
    var server by remember { mutableStateOf<WebEditServer?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { server?.stop() } }

    Section("Senderliste & YouTube am Handy/PC bearbeiten") {
        val s = server
        if (s == null) {
            Text(
                "Gruppen und Sender im Browser per Ziehen sortieren, ein- und ausblenden, Gruppen umbenennen. " +
                    "Gleiches WLAN nötig; mit Code geschützt.",
                color = Palette.TextMuted, fontSize = 13.sp,
            )
            TvButton("Web-Bearbeitung starten", onClick = {
                val ip = PhoneInputServer.localAddress()
                if (ip == null) {
                    app.message = "Keine WLAN-/LAN-Adresse gefunden. Ist das Gerät mit dem Heimnetz verbunden?"
                    return@TvButton
                }
                val srv = WebEditServer(
                    groups = { app.editGroups },
                    hiddenChannels = { app.hiddenChannels },
                    onSave = { edits, snap -> app.applyWebEdit(edits, snap) },
                    onStopped = { server = null },
                    yt = object : de.aze.iptv.data.YtBridge {
                        override fun list() = app.youtube
                        override fun add(link: String, name: String) = app.addYoutube(link, name)
                        override fun remove(id: String) { app.youtube.firstOrNull { it.id == id }?.let { app.removeYoutube(it) } }
                        override fun import(entries: List<de.aze.iptv.data.YtEntry>) = app.importYoutube(entries)
                    },
                )
                if (srv.start()) {
                    address = "http://$ip:${srv.port}"
                    server = srv
                } else {
                    app.message = "Web-Bearbeitung konnte nicht gestartet werden."
                }
            })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                // QR-Code: mit der Handy-Kamera scannen – öffnet die Seite mit dem Code schon eingetragen
                QrCode("${address ?: ""}/?c=${s.code}")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QR-Code mit der Handy-Kamera scannen – oder im Browser öffnen:", color = Palette.TextSoft, fontSize = 14.sp)
                    Text(address ?: "", color = Palette.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Code:", color = Palette.TextSoft, fontSize = 18.sp)
                        Text(s.code, color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Bleibt offen, solange du bearbeitest; schließt sich 30 Minuten nach der letzten Änderung.", color = Palette.TextMuted, fontSize = 13.sp)
            TvButton("Beenden", onClick = { s.stop() })
        }
    }
}

@Composable
private fun YouTubeSection(app: AppController) {
    Section("YouTube Live") {
        Text("Abspielen", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Im Player der App", selected = !app.youtubeExternal, onClick = { app.updateYoutubeExternal(false) })
            TvButton("In der YouTube-App", selected = app.youtubeExternal, onClick = { app.updateYoutubeExternal(true) })
        }
        Text(
            "Zeigt dein Gerät bei YouTube-Sendern nur Ton ohne Bild, „In der YouTube-App“ wählen: Der Sender startet dann " +
                "in der offiziellen YouTube-App, mit Zurück geht es hierher zurück.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        Text(
            "YouTube-Livesender erscheinen als eigene Gruppe „YouTube Live“. Am besten den Kanal-Link eintragen " +
                "(z. B. youtube.com/@trthaber) – dann läuft immer der aktuelle Livestream. " +
                "Es laufen nur Kanäle, die das Einbetten erlauben.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        app.youtube.forEachIndexed { i, e ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(e.name, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (e.isChannel) "Kanal · immer der aktuelle Livestream" else "Einzelnes Video", color = Palette.TextMuted, fontSize = 14.sp)
                }
                if (i > 0) TvButton("▲", onClick = { app.moveYoutube(e, -1) })
                if (i < app.youtube.size - 1) TvButton("▼", onClick = { app.moveYoutube(e, +1) })
                TvButton("Löschen", danger = true, onClick = { app.removeYoutube(e) })
            }
        }
        val open = de.aze.iptv.data.YouTube.suggestions.filter { (n, _) -> app.youtube.none { it.name == n } }
        if (open.isNotEmpty()) {
            Text("Vorschläge (offizielle Kanäle, frei auf YouTube) – antippen zum Hinzufügen:", color = Palette.TextSoft, fontSize = 14.sp)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                open.forEach { (n, ref) -> TvButton("+ $n", onClick = { app.addYoutube(ref, n) }) }
            }
        }

        var name by remember { mutableStateOf("") }
        var link by remember { mutableStateOf("") }
        Text("Eigener Kanal:", color = Palette.TextSoft, fontSize = 14.sp)
        InputRow("Name", name, placeholder = "z. B. TRT Haber") { name = it }
        InputRow("Kanal- oder Video-Link", link, placeholder = "youtube.com/@kanal", keyboard = KeyboardType.Uri) { link = it }
        TvButton(if (link.isBlank()) "Hinzufügen (Link fehlt)" else "Hinzufügen", onClick = {
            if (link.isBlank()) return@TvButton
            app.addYoutube(link, name)
            name = ""; link = ""
        })
    }
}

@Composable
private fun PlaybackSection(app: AppController, onEditList: () -> Unit, part: Int = 0) {
    if (part != 2) Section("Farbschema") {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Schemes.forEachIndexed { i, sc ->
                TvButton(sc.name, selected = app.schemeIndex == i, onClick = { app.updateScheme(i) })
            }
        }
        Text("Schriftfarbe", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextColors.forEachIndexed { i, (name, _) ->
                TvButton(name, selected = app.textColorIndex == i, onClick = { app.updateTextColor(i) })
            }
        }
        TvButton("Senderliste bearbeiten (Gruppen und Sender sortieren, ausblenden)", onClick = onEditList)
    }
    if (part != 2) WebEditSection(app)
    if (part != 2) Section("Filme: IMDb-Bewertung") {
        Text(
            "Die Bewertung des Anbieters (★ von 10) wird immer angezeigt. Für die echte IMDb-Bewertung einen kostenlosen " +
                "Schlüssel auf omdbapi.com holen (\"API Key\" → FREE, kommt per E-Mail) und hier eintragen.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        InputRow("OMDb-Schlüssel", app.omdbKey, placeholder = "z. B. a1b2c3d4") { app.updateOmdbKey(it) }
    }
    if (part != 1) Section("Ton-Bild-Synchron (alle Sender)") {
        Text(
            "Ist der Ton bei allen Sendern versetzt (z. B. durch Soundbar oder AV-Receiver), hier einstellen. " +
                "Für einzelne Sender: im Bild OK lang → „Lippensynchron“ – das gilt dann zusätzlich nur für diesen Sender.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(msLabel(app.audioDelay), color = Palette.Highlight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TvButton("− 100", onClick = { app.updateAudioDelay(app.audioDelay - 100) })
            TvButton("− 20", onClick = { app.updateAudioDelay(app.audioDelay - 20) })
            TvButton("+ 20", onClick = { app.updateAudioDelay(app.audioDelay + 20) })
            TvButton("+ 100", onClick = { app.updateAudioDelay(app.audioDelay + 100) })
            TvButton("0", onClick = { app.updateAudioDelay(0) })
        }
        Text("Plus = Ton später, Minus = Ton früher.", color = Palette.TextMuted, fontSize = 12.sp)
        if (app.audioDelays.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${app.audioDelays.size} Sender mit eigenem Wert", color = Palette.TextSoft, fontSize = 13.sp)
                TvButton("Alle Senderwerte löschen", onClick = { app.clearChannelDelays() })
            }
        }
    }
    if (part != 1) Section("Wiedergabe") {
      // Puffer, Player, Bildqualität und Bildwiederholrate immer sichtbar (auf Wunsch von Aziz)
      run {
        Text("Puffer", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BufferLevel.entries.forEach { lvl ->
                TvButton(lvl.label, selected = app.bufferLevel == lvl, onClick = { app.updateBuffer(lvl) })
            }
        }
        Text(app.bufferLevel.hint, color = Palette.TextMuted, fontSize = 13.sp)
        Text("Player", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            de.aze.iptv.player.PlayerEngine.entries.forEach { e ->
                TvButton(e.label, selected = app.playerEngine == e, onClick = { app.updateEngine(e) })
            }
        }
        Text(
            "Automatisch: Media3 spielt ab; kann es einen Sender nicht abspielen, übernimmt VLC für diesen Sender. " +
                "Im Schnellmenü lässt sich das je Sender auch von Hand umschalten.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        Text("Bildqualität", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Immer beste Stufe", selected = app.forceHighest, onClick = { app.updateForceHighest(true) })
            TvButton("Automatisch", selected = !app.forceHighest, onClick = { app.updateForceHighest(false) })
        }
        Text("Bildwiederholrate", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Nie umschalten", selected = !app.afr && !app.afr50, onClick = { app.updateAfr(false); app.updateAfr50(false) })
            TvButton("50 Hz in der App", selected = app.afr50, onClick = { app.updateAfr50(true) })
            TvButton("Je Sender", selected = app.afr, onClick = { app.updateAfr(true) })
        }
        Text(
            "Am ruhigsten ohne jedes Umschalten: „Nie umschalten“ wählen und die Frequenz einmal fest im Gerät einstellen – " +
                "SHIELD: Einstellungen → Gerät → Display & Ton → Auflösung → z. B. „4K 50 Hz“ bzw. „1080p 50 Hz“; " +
                "Fire TV: Einstellungen → Display & Töne → Display → Videoauflösung → „… 50 Hz“. " +
                "„50 Hz in der App“ stellt beim Öffnen der App um (beim Verlassen schaltet das Gerät zurück). " +
                "„Je Sender“ stellt bei jedem Sender passend um.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(if (app.showDiag) "Fehlersuche-Anzeige: an" else "Fehlersuche-Anzeige: aus", selected = app.showDiag, onClick = { app.updateShowDiag(!app.showDiag) })
        }
      }
        Text("Untertitel", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Aus", selected = !app.subtitlesOn, onClick = { app.updateSubtitles(false) })
            TvButton("An (wenn vorhanden)", selected = app.subtitlesOn, onClick = { app.updateSubtitles(true) })
        }
        Text("Bildformat", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AspectMode.entries.forEach { m ->
                TvButton(m.label, selected = app.aspectMode == m, onClick = { app.updateAspect(m) })
            }
        }
        Text(
            "Schnell im Bild ändern: OK lang drücken oder Menütaste (Touch: lange tippen). " +
                "Dort gibt es Bild, Ton, Fußball-Videotext, Player und Puffer.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
    }
}

@Composable
private fun NetworkSection(app: AppController) {
    val context = LocalContext.current
    var vpn by remember { mutableStateOf(Network.vpnActive(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            vpn = Network.vpnActive(context)
        }
    }
    Section("Netzwerk") {
        Text(
            if (vpn) "VPN auf diesem Gerät: aktiv" else "VPN auf diesem Gerät: nicht aktiv",
            color = if (vpn) Palette.Accent else Palette.TextSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        val p = app.proxy
        var type by remember { mutableStateOf(p.type) }
        var host by remember { mutableStateOf(p.host) }
        var port by remember { mutableStateOf(if (p.port > 0) p.port.toString() else "") }
        var user by remember { mutableStateOf(p.user) }
        var pass by remember { mutableStateOf(p.pass) }

        Text("Proxy nur für diese App (optional)", color = Palette.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProxyType.entries.forEach { t ->
                TvButton(t.label, selected = type == t, onClick = { type = t })
            }
        }
        if (type != ProxyType.OFF) {
            InputRow("Proxy-Adresse", host, placeholder = "z. B. proxy.anbieter.com", keyboard = KeyboardType.Uri) { host = it }
            InputRow("Port", port, placeholder = "z. B. 1080", keyboard = KeyboardType.Number) { port = it.filter(Char::isDigit) }
            InputRow("Benutzer (falls nötig)", user) { user = it }
            InputRow("Passwort (falls nötig)", pass, password = true) { pass = it }
        }
        val pending = ProxySettings(type, host.trim(), port.toIntOrNull() ?: 0, user.trim(), pass)
        TvButton(if (pending == p) "Gespeichert" else "Proxy-Einstellung übernehmen", onClick = {
            app.updateProxy(pending)
            app.message = when {
                pending.type == ProxyType.OFF -> "Proxy ausgeschaltet."
                pending.active -> "Proxy aktiv: Verbindungen laufen über ${pending.host}:${pending.port}."
                else -> "Proxy unvollständig: Adresse und Port angeben."
            }
        })
    }
}

@Composable
private fun FavoritesSection(app: AppController) {
    Section("Favoriten (Nummer 1, 2, 3 …)") {
        val favs = app.lineup.groups.firstOrNull { it.isFavorites }?.channels.orEmpty()
        if (favs.isEmpty()) {
            Text("In der Senderliste OK lang drücken (Touch: lange tippen), um einen Favoriten hinzuzufügen.", color = Palette.TextMuted, fontSize = 14.sp)
        }
        favs.forEach { nc ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${nc.number}", color = Palette.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 40.dp))
                Text(nc.channel.name, color = Palette.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TvButton("▲", onClick = { app.moveFavorite(nc.channel, -1) })
                TvButton("▼", onClick = { app.moveFavorite(nc.channel, +1) })
                TvButton("Entfernen", danger = true, onClick = { app.toggleFavorite(nc.channel) })
            }
        }
    }
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Release?>(null) }
    var busy by remember { mutableStateOf(false) }

    Section("App-Update") {
        Text("Installiert: Version ${BuildConfig.VERSION_NAME}", color = Palette.TextSoft, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Nach Update suchen", onClick = {
                if (busy) return@TvButton
                busy = true
                status = "Suche …"
                scope.launch {
                    try {
                        release = Updater.check()
                        status = release?.let { "Neue Version ${it.versionName} verfügbar." } ?: "Die App ist aktuell."
                    } catch (e: Exception) {
                        status = "Update-Prüfung fehlgeschlagen: ${e.message}"
                    }
                    busy = false
                }
            })
            release?.let { rel ->
                TvButton("Version ${rel.versionName} installieren", onClick = {
                    if (busy) return@TvButton
                    busy = true
                    scope.launch {
                        try {
                            val apk = Updater.download(context, rel) { p -> status = "Lade herunter … $p %" }
                            status = if (Updater.install(context, apk)) "Installation gestartet."
                            else "Bitte erlauben, dass Aze-IPTV-Player Apps installieren darf, dann erneut auf Installieren drücken."
                        } catch (e: Exception) {
                            status = "Download fehlgeschlagen: ${e.message}"
                        }
                        busy = false
                    }
                })
            }
        }
        status?.let { Text(it, color = Palette.TextMuted, fontSize = 13.sp) }
        release?.notes?.takeIf { it.isNotBlank() }?.let { Text("Neu: $it", color = Palette.TextMuted, fontSize = 13.sp) }
    }
}

@Composable
private fun AboutSection(app: AppController) {
    val context = LocalContext.current
    var size by remember { mutableStateOf<Long?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { size = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { de.aze.iptv.data.CacheCleaner.sizeMb(context) } }
    Section("Zwischenspeicher") {
        Text(
            "Wird bei jedem Start automatisch aufgeräumt (Reste von YouTube, Videotext, Updates). " +
                "Senderlogos und Filmplakate bleiben gespeichert (höchstens 250 MB), damit die Listen schnell aufgehen. " +
                "Playlists, Favoriten, Einstellungen und EPG werden nie gelöscht.",
            color = Palette.TextMuted, fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Belegt: ${size?.let { "$it MB" } ?: "…"}", color = Palette.TextSoft, fontSize = 13.sp)
            TvButton(if (clearing) "Wird geleert …" else "Alles leeren (auch Logos)", onClick = {
                if (clearing) return@TvButton
                clearing = true
                scope.launch {
                    try {
                        de.aze.iptv.data.CacheCleaner.clearAll(context)
                        size = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { de.aze.iptv.data.CacheCleaner.sizeMb(context) }
                        app.message = "Zwischenspeicher geleert – Logos werden beim Anzeigen neu geladen."
                    } finally {
                        clearing = false
                    }
                }
            })
        }
    }
    Section("Über") {
        Text(
            "Aze-IPTV-Player ist ein Player und liefert selbst keine Sender oder Inhalte. " +
                "Bitte nur Playlists verwenden, für die du die Nutzungsrechte hast.",
            color = Palette.TextSoft, fontSize = 13.sp,
        )
        Text(
            "Verwendete Open-Source-Bausteine: AndroidX Media3 (Wiedergabe), Jetpack Compose (Oberfläche), " +
                "AndroidX Core/Activity/Lifecycle und Kotlin Coroutines – jeweils unter der Apache License 2.0 " +
                "(apache.org/licenses/LICENSE-2.0). libVLC (VideoLAN) unter der GNU LGPL 2.1 (gnu.org/licenses/lgpl-2.1), " +
                "unverändert eingebunden. SkorText-Schriften: DejaVu Fonts License.",
            color = Palette.TextMuted, fontSize = 14.sp,
        )
    }
}

/** Eingabezeile, die per OK ein Eingabefenster öffnet – so springt die Tastatur auf dem TV nicht ungewollt auf. */
@Composable
private fun InputRow(
    label: String,
    value: String,
    placeholder: String = "",
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Focusable(modifier = Modifier.fillMaxWidth(), onClick = { editing = true }) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(if (focused) Palette.Accent else Palette.Raised, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(label, color = if (focused) Palette.OnAccent else Palette.TextMuted, fontSize = 13.sp)
            val shown = when {
                value.isEmpty() -> placeholder.ifEmpty { "–" }
                password -> "•".repeat(value.length.coerceAtMost(12))
                keyboard == KeyboardType.Uri -> Secure.shortUrl(value)
                else -> value
            }
            Text(shown, color = if (focused) Palette.OnAccent else if (value.isEmpty()) Palette.TextMuted else Palette.Text, fontSize = 17.sp, maxLines = 1)
        }
    }
    if (editing) {
        InputDialog(label, value, password, keyboard, onDone = {
            onChange(it)
            editing = false
        }, onCancel = { editing = false })
    }
}

@Composable
internal fun InputDialog(
    label: String,
    initial: String,
    password: Boolean,
    keyboard: KeyboardType,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(Palette.Panel, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(label, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text,
                    focusedBorderColor = Palette.Accent,
                    unfocusedBorderColor = Palette.Line,
                    cursorColor = Palette.Accent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Übernehmen", onClick = { onDone(text) })
                TvButton("Abbrechen", onClick = onCancel)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
