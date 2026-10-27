package de.aze.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    val scroll = rememberScrollState()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(modifier = Modifier.fillMaxSize().background(Palette.Ground), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Einstellungen", color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TvButton("Zurück zum Fernsehen", modifier = Modifier.focusRequester(firstFocus), onClick = onClose)
            }

            app.message?.let {
                Text(it, color = Palette.TextSoft, fontSize = 16.sp)
            }

            PhoneInputSection(app)
            PlaylistSection(app)
            RefreshSection(app)
            PlaybackSection(app)
            FavoritesSection(app)
            NetworkSection(app)
            Section("Sicherheit") {
                Text(
                    if (app.pinIsDefault) "Die PIN ist noch 0000. Bitte eine eigene PIN festlegen."
                    else "Die Einstellungen sind mit deiner PIN geschützt.",
                    color = if (app.pinIsDefault) Palette.Accent else Palette.TextSoft,
                    fontSize = 16.sp,
                )
                Text(
                    "Zugangsdaten werden verschlüsselt gespeichert, Links nur gekürzt angezeigt, " +
                        "und die App macht keine Sicherungen in die Cloud oder auf andere Geräte.",
                    color = Palette.TextMuted, fontSize = 15.sp,
                )
                TvButton("PIN ändern", onClick = { pinStep = 1 })
            }
            UpdateSection()
            AboutSection()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Panel, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun PlaylistSection(app: AppController) {
    Section("Playlists") {
        if (app.sources.isEmpty()) {
            Text("Noch keine Playlist. Füge unten einen M3U-Link oder Server + Login hinzu.", color = Palette.TextMuted, fontSize = 16.sp)
        }
        if (app.sources.size > 1) {
            Text(
                "Alle Playlists erscheinen in der Senderliste untereinander, in dieser Reihenfolge. " +
                    "Oben in der Senderliste kannst du auch nur eine Playlist anzeigen lassen.",
                color = Palette.TextMuted, fontSize = 15.sp,
            )
        }
        app.sources.forEachIndexed { i, src ->
            var renaming by remember(src.id) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(src.name, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (src.type == SourceType.M3U) "M3U-Link · " else "Server + Login · ") + Secure.shortUrl(src.url),
                        color = Palette.TextMuted,
                        fontSize = 14.sp,
                    )
                }
                if (app.sources.size > 1) {
                    if (i > 0) TvButton("▲", onClick = { app.moveSource(src, -1) })
                    if (i < app.sources.size - 1) TvButton("▼", onClick = { app.moveSource(src, +1) })
                }
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

        Text("Playlist hinzufügen", color = Palette.TextSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("M3U-Link", selected = mode == SourceType.M3U, onClick = { mode = SourceType.M3U })
            TvButton("Server + Login", selected = mode == SourceType.XTREAM, onClick = { mode = SourceType.XTREAM })
        }
        InputRow("Name", name, placeholder = "z. B. Mein Anbieter") { name = it }
        if (mode == SourceType.M3U) {
            InputRow("M3U-Link (komplett)", url, placeholder = "http://…", keyboard = KeyboardType.Uri) { url = it }
        } else {
            InputRow("Server-Adresse", url, placeholder = "http://server:port", keyboard = KeyboardType.Uri) { url = it }
            InputRow("Benutzer", user) { user = it }
            InputRow("Passwort", pass, password = true) { pass = it }
        }
        val valid = url.isNotBlank() && (mode == SourceType.M3U || (user.isNotBlank() && pass.isNotBlank()))
        TvButton(if (valid) "Speichern und laden" else "Speichern und laden (Angaben fehlen)", onClick = {
            if (!valid) return@TvButton
            app.addSource(
                PlaylistSource(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { if (mode == SourceType.M3U) "Playlist" else "Anbieter" },
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
        Text("Zuletzt aktualisiert: $last", color = Palette.TextMuted, fontSize = 15.sp)
        TvButton(if (app.loading) "Wird aktualisiert …" else "Jetzt aktualisieren", onClick = { app.refreshNow() })
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
                color = Palette.TextMuted, fontSize = 15.sp,
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
            Text("Am Handy im Browser öffnen:", color = Palette.TextSoft, fontSize = 16.sp)
            Text(address ?: "", color = Palette.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Code:", color = Palette.TextSoft, fontSize = 18.sp)
                Text(s.code, color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Text("Schließt sich nach der Eingabe oder nach 5 Minuten von selbst.", color = Palette.TextMuted, fontSize = 15.sp)
            TvButton("Beenden", onClick = { s.stop() })
        }
    }
}

@Composable
private fun PlaybackSection(app: AppController) {
    Section("Wiedergabe") {
        Text("Puffer", color = Palette.TextSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BufferLevel.entries.forEach { lvl ->
                TvButton(lvl.label, selected = app.bufferLevel == lvl, onClick = { app.updateBuffer(lvl) })
            }
        }
        Text(app.bufferLevel.hint, color = Palette.TextMuted, fontSize = 15.sp)
        Text("Bildformat", color = Palette.TextSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AspectMode.entries.forEach { m ->
                TvButton(m.label, selected = app.aspectMode == m, onClick = { app.updateAspect(m) })
            }
        }
        Text(
            "Schnell im Bild ändern: OK lang drücken oder Menütaste (Touch: lange tippen). " +
                "Dort gibt es Bild, Ton, Untertitel und Puffer.",
            color = Palette.TextMuted, fontSize = 15.sp,
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
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        val p = app.proxy
        var type by remember { mutableStateOf(p.type) }
        var host by remember { mutableStateOf(p.host) }
        var port by remember { mutableStateOf(if (p.port > 0) p.port.toString() else "") }
        var user by remember { mutableStateOf(p.user) }
        var pass by remember { mutableStateOf(p.pass) }

        Text("Proxy nur für diese App (optional)", color = Palette.TextSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
            Text("In der Senderliste OK lang drücken (Touch: lange tippen), um einen Favoriten hinzuzufügen.", color = Palette.TextMuted, fontSize = 16.sp)
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
        Text("Installiert: Version ${BuildConfig.VERSION_NAME}", color = Palette.TextSoft, fontSize = 16.sp)
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
        status?.let { Text(it, color = Palette.TextMuted, fontSize = 15.sp) }
        release?.notes?.takeIf { it.isNotBlank() }?.let { Text("Neu: $it", color = Palette.TextMuted, fontSize = 15.sp) }
    }
}

@Composable
private fun AboutSection() {
    Section("Über") {
        Text(
            "Aze-IPTV-Player ist ein Player und liefert selbst keine Sender oder Inhalte. " +
                "Bitte nur Playlists verwenden, für die du die Nutzungsrechte hast.",
            color = Palette.TextSoft, fontSize = 15.sp,
        )
        Text(
            "Verwendete Open-Source-Bausteine: AndroidX Media3 (Wiedergabe), Jetpack Compose (Oberfläche), " +
                "AndroidX Core/Activity/Lifecycle und Kotlin Coroutines – jeweils unter der Apache License 2.0 " +
                "(apache.org/licenses/LICENSE-2.0).",
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
private fun InputDialog(
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
