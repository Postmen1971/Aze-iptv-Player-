package de.aze.iptv.data

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/** Eine Gruppe, wie sie aus der Web-Oberfläche zurückkommt (in der neuen Reihenfolge). */
class WebGroupEdit(
    val key: String,
    val name: String,
    val hidden: Boolean,
    /** Sender in neuer Reihenfolge: Schlüssel + ausgeblendet */
    val channels: List<Pair<String, Boolean>>,
)

/**
 * Senderliste am Handy/PC bearbeiten: Gruppen und Sender per Ziehen sortieren, ein- und ausblenden, Gruppen umbenennen.
 * Schutz wie bei der Handy-Eingabe:
 *  - nur aus dem Heimnetz, nur mit dem 4-stelligen Code vom Fernseher (nach 10 falschen Codes ist Schluss)
 *  - beendet sich 30 Minuten nach der letzten Benutzung
 *  - Sender-Links (die oft Zugangsdaten enthalten) verlassen das Gerät nicht – das Handy sieht nur Namen und Nummern.
 */
class WebEditServer(
    /** Aktuelle Gruppen (inkl. ausgeblendeter). Wird im Hintergrund gelesen. */
    private val groups: () -> List<EditGroup>,
    /** Ausgeblendete Sender (Schlüssel). */
    private val hiddenChannels: () -> Set<String>,
    /** Wird auf dem Haupt-Thread aufgerufen. */
    private val onSave: (edits: List<WebGroupEdit>, snapshot: List<EditGroup>) -> Unit,
    private val onStopped: () -> Unit,
) {
    val code: String = (SecureRandom().nextInt(9000) + 1000).toString()
    var port: Int = 0
        private set

    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())
    private var wrongCodes = 0
    @Volatile private var running = false

    /** Stand der Liste, den das Handy zuletzt bekommen hat (Nummern beziehen sich darauf). */
    @Volatile private var snapshot: List<EditGroup> = emptyList()
    @Volatile private var version = 0

    fun start(): Boolean {
        val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(8081)) } }
            .recoverCatching { ServerSocket(0) }
            .getOrNull() ?: return false
        server = s
        port = s.localPort
        running = true
        Thread({ acceptLoop(s) }, "web-edit").apply { isDaemon = true }.start()
        touch()
        return true
    }

    private val timeout = Runnable { stop() }

    private fun touch() {
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 30 * 60 * 1000L)
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { server?.close() }
        main.removeCallbacks(timeout)
        main.post { onStopped() }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val client = runCatching { s.accept() }.getOrNull() ?: break
            // Jede Anfrage in eigenem Thread, damit eine große Liste die Seite nicht blockiert
            Thread({
                runCatching { handle(client) }
                runCatching { client.close() }
            }, "web-edit-req").apply { isDaemon = true }.start()
        }
    }

    private fun isLocal(addr: InetAddress): Boolean =
        addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress

    private fun handle(socket: Socket) {
        socket.soTimeout = 20_000
        val out = socket.getOutputStream()
        if (!isLocal(socket.inetAddress)) {
            respond(out, 403, "text/plain", "Nur aus dem eigenen Heimnetz möglich.")
            return
        }
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        var length = 0
        var sentCode: String? = null
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            val lower = h.lowercase()
            if (lower.startsWith("content-length:")) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
            if (lower.startsWith("x-code:")) sentCode = h.substringAfter(':').trim()
        }
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: ""
        val path = parts.getOrNull(1) ?: "/"

        // Fehlersuche: zuletzt von liveonsat.com geladene Seite herunterladen (Code als ?c=…)
        if (path.startsWith("/liveonsat")) {
            val c = Regex("[?&]c=(\\d{4})").find(path)?.groupValues?.get(1)
            if (c != code) {
                respond(out, 403, "text/plain; charset=utf-8", "Code fehlt: /liveonsat?c=CODE")
                return
            }
            touch()
            respond(out, 200, "text/plain; charset=utf-8", LiveOnSat.lastHtml ?: "Noch nichts geladen – erst am Fernseher „Fußball heute“ öffnen.")
            return
        }
        if (!path.startsWith("/api/")) {
            respond(out, 200, "text/html; charset=utf-8", PAGE)
            return
        }
        if (sentCode != code) {
            wrongCodes++
            respond(out, 403, "application/json", """{"error":"Falscher Code"}""")
            if (wrongCodes >= 10) stop()
            return
        }
        touch()
        when {
            method == "GET" && path.startsWith("/api/list") -> respond(out, 200, "application/json", listJson())
            method == "POST" && path.startsWith("/api/save") -> {
                if (length <= 0 || length > 8 * 1024 * 1024) {
                    respond(out, 400, "application/json", """{"error":"Ungültige Daten"}""")
                    return
                }
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val result = runCatching { save(String(body, 0, read, Charsets.UTF_8)) }
                result.fold(
                    onSuccess = { respond(out, 200, "application/json", """{"ok":true}""") },
                    onFailure = { e ->
                        val msg = JSONObject().put("error", e.message ?: "Fehler").toString()
                        respond(out, if (e is Conflict) 409 else 400, "application/json", msg)
                    },
                )
            }
            else -> respond(out, 404, "application/json", """{"error":"?"}""")
        }
    }

    private fun listJson(): String {
        val list = groups()
        val hidden = hiddenChannels()
        snapshot = list
        version++
        val arr = JSONArray()
        list.forEach { g ->
            val ch = JSONArray()
            g.channels.forEach { c -> ch.put(JSONArray().put(c.name).put(if (c.key in hidden) 1 else 0)) }
            arr.put(
                JSONObject()
                    .put("n", g.name).put("o", g.originalName).put("s", g.sourceName ?: "")
                    .put("h", if (g.hidden) 1 else 0).put("c", ch),
            )
        }
        return JSONObject().put("v", version).put("groups", arr).toString()
    }

    private class Conflict : Exception("Die Senderliste hat sich inzwischen geändert – bitte Seite neu laden.")

    private fun save(text: String) {
        val o = JSONObject(text)
        val snap = snapshot
        if (o.optInt("v") != version || snap.isEmpty()) throw Conflict()
        val arr = o.getJSONArray("groups")
        if (arr.length() != snap.size) throw IllegalArgumentException("Gruppen unvollständig")
        val seenGroups = HashSet<Int>()
        val edits = (0 until arr.length()).map { i ->
            val g = arr.getJSONObject(i)
            val gi = g.getInt("i")
            require(gi in snap.indices && seenGroups.add(gi)) { "Ungültige Gruppe" }
            val orig = snap[gi]
            val c = g.getJSONArray("c")
            require(c.length() == orig.channels.size) { "Sender unvollständig in „${orig.name}“" }
            val seen = HashSet<Int>()
            val channels = (0 until c.length()).map { k ->
                val pair = c.getJSONArray(k)
                val ci = pair.getInt(0)
                require(ci in orig.channels.indices && seen.add(ci)) { "Ungültiger Sender" }
                orig.channels[ci].key to (pair.optInt(1) == 1)
            }
            WebGroupEdit(orig.key, g.optString("n").take(80), g.optInt("h") == 1, channels)
        }
        val done = java.util.concurrent.CountDownLatch(1)
        main.post {
            try { onSave(edits, snap) } finally { done.countDown() }
        }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        // Neuer Stand: das Handy muss die Liste neu laden, bevor es wieder speichert.
        version++
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8_192) return null
            sb.append(c.toChar())
        }
    }

    private fun respond(out: OutputStream, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 409 -> "Conflict"; else -> "Forbidden" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        private val PAGE = """
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Aze-IPTV-Player · Senderliste</title>
<style>
:root{--bg:#0E1116;--panel:#151A21;--raised:#1F2630;--line:#2A3340;--text:#EDEFF3;--soft:#B9C1CC;--muted:#7F8A99;--acc:#5AA0FF;--warn:#F07A6A}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,sans-serif;font-size:15px}
header{position:sticky;top:0;z-index:5;background:var(--bg);border-bottom:1px solid var(--line);padding:12px 16px;display:flex;gap:10px;align-items:center;flex-wrap:wrap}
header h1{font-size:17px;margin:0;flex:1;min-width:160px}
button{font:inherit;border:0;border-radius:8px;background:var(--raised);color:var(--text);padding:8px 12px;cursor:pointer}
button.pri{background:var(--acc);color:#0B1220;font-weight:700}
button:disabled{opacity:.5}
input{font:inherit;width:100%;height:44px;border-radius:10px;border:1px solid var(--line);background:var(--panel);color:var(--text);padding:0 12px}
main{max-width:820px;margin:0 auto;padding:12px 16px 90px}
.hint{color:var(--muted);font-size:13px;margin:4px 0 12px}
.row{background:var(--panel);border:1px solid var(--line);border-radius:10px;margin:6px 0}
.head{display:flex;align-items:center;gap:8px;padding:6px 8px;min-height:46px}
.h{touch-action:none;cursor:grab;color:var(--muted);font-size:20px;padding:6px 8px;user-select:none;-webkit-user-select:none}
.name{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.name small{color:var(--muted);margin-left:6px}
.num{color:var(--acc);font-weight:700;min-width:34px;text-align:right;font-variant-numeric:tabular-nums}
.off .name,.off .num{opacity:.4;text-decoration:line-through}
.ico{background:transparent;padding:6px 8px;color:var(--soft)}
.chs{padding:0 8px 8px 30px}
.chs .row{background:var(--raised);margin:4px 0}
.chs .head{min-height:40px}
.drag{position:fixed;z-index:20;pointer-events:none;opacity:.92;box-shadow:0 8px 24px rgba(0,0,0,.5)}
.ph{border:2px dashed var(--acc);border-radius:10px;margin:6px 0}
#bar{position:fixed;left:0;right:0;bottom:0;background:var(--panel);border-top:1px solid var(--line);padding:10px 16px;display:flex;gap:10px;align-items:center;justify-content:center}
#msg{color:var(--soft);font-size:14px;flex:1;max-width:520px}
#login{max-width:360px;margin:60px auto;padding:0 16px}
#login label{display:block;color:var(--soft);margin:0 0 8px}
#login button{margin-top:14px;width:100%;height:46px}
.err{color:var(--warn)}
</style></head><body>
<div id="login">
  <h1 style="font-size:20px">Senderliste bearbeiten</h1>
  <label for="code">Code vom Fernseher</label>
  <input id="code" inputmode="numeric" maxlength="4" autocomplete="off">
  <button class="pri" id="go">Öffnen</button>
  <p id="lerr" class="err"></p>
</div>
<div id="app" hidden>
  <header><h1>Senderliste</h1><button id="reload">Neu laden</button></header>
  <main>
    <p class="hint">≡ gedrückt halten und ziehen zum Verschieben · ⤒ an den Anfang · 👁 ein-/ausblenden · ✎ umbenennen · Gruppe antippen zeigt ihre Sender. Die Nummern zählen ohne deine Favoriten.</p>
    <div id="groups"></div>
  </main>
  <div id="bar"><span id="msg">Keine Änderungen</span><button class="pri" id="save" disabled>Speichern</button></div>
</div>
<script>
var CODE='',DATA=null,dirty=false;
function ${'$'}(id){return document.getElementById(id)}
try{CODE=sessionStorage.getItem('azeCode')||''}catch(e){}
function api(path,body){
  return fetch(path,{method:body?'POST':'GET',headers:{'X-Code':CODE,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined})
    .then(function(r){return r.json().then(function(j){if(!r.ok)throw new Error(j.error||('Fehler '+r.status));return j})});
}
function login(){
  CODE=${'$'}('code').value.trim();
  load().then(function(){try{sessionStorage.setItem('azeCode',CODE)}catch(e){}}).catch(function(e){${'$'}('lerr').textContent=e.message});
}
${'$'}('go').onclick=login;
${'$'}('code').onkeydown=function(e){if(e.key==='Enter')login()};
${'$'}('reload').onclick=function(){if(!dirty||confirm('Änderungen verwerfen?'))load().catch(function(e){msg(e.message,true)})};
${'$'}('save').onclick=save;
function load(){
  return api('/api/list').then(function(j){
    DATA={v:j.v,groups:j.groups.map(function(g,i){return{i:i,n:g.n,o:g.o,s:g.s,h:g.h,open:false,c:g.c.map(function(c,k){return{i:k,n:c[0],h:c[1]}})}})};
    ${'$'}('login').hidden=true;${'$'}('app').hidden=false;setDirty(false);render();
  });
}
function msg(t,err){var m=${'$'}('msg');m.textContent=t;m.className=err?'err':''}
function setDirty(d){dirty=d;${'$'}('save').disabled=!d;msg(d?'Ungespeicherte Änderungen':'Keine Änderungen')}
function el(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;if(text!=null)e.textContent=text;return e}
function btn(text,title,fn){var b=el('button','ico',text);b.title=title;b.onclick=function(ev){ev.stopPropagation();fn()};return b}
function render(){
  var box=${'$'}('groups');box.innerHTML='';
  DATA.groups.forEach(function(g){box.appendChild(groupRow(g))});
  numberAll();
}
function groupRow(g){
  var row=el('div','row'+(g.h?' off':''));row.dataset.i=g.i;row._g=g;
  var head=el('div','head');
  head.appendChild(el('span','h','≡'));
  var name=el('span','name',g.n);
  name.appendChild(el('small',null,g.c.length+' Sender'+(g.s?' · '+g.s:'')));
  head.appendChild(name);
  head.appendChild(btn('⤒','An den Anfang',function(){DATA.groups.splice(DATA.groups.indexOf(g),1);DATA.groups.unshift(g);render();setDirty(true)}));
  head.appendChild(btn('✎','Umbenennen',function(){var n=prompt('Name der Gruppe (leer = Original „'+g.o+'“)',g.n);if(n===null)return;g.n=n.trim()||g.o;render();setDirty(true)}));
  head.appendChild(btn('👁','Ein-/ausblenden',function(){g.h=g.h?0:1;row.classList.toggle('off',!!g.h);numberAll();setDirty(true)}));
  head.onclick=function(e){if(e.target.closest('.h'))return;g.open=!g.open;var old=row.querySelector('.chs');if(old)old.remove();if(g.open)row.appendChild(channelList(g));numberAll()};
  row.appendChild(head);
  if(g.open)row.appendChild(channelList(g));
  return row;
}
function channelList(g){
  var box=el('div','chs');
  g.c.forEach(function(c){box.appendChild(channelRow(g,c))});
  sortable(box,function(){g.c=[].map.call(box.children,function(r){return r._c});numberAll();setDirty(true)});
  return box;
}
function channelRow(g,c){
  var row=el('div','row'+(c.h?' off':''));row._c=c;
  var head=el('div','head');
  head.appendChild(el('span','h','≡'));
  head.appendChild(el('span','num',''));
  head.appendChild(el('span','name',c.n));
  head.appendChild(btn('⤒','An den Anfang der Gruppe',function(){g.c.splice(g.c.indexOf(c),1);g.c.unshift(c);var box=row.parentNode;box.insertBefore(row,box.firstChild);numberAll();setDirty(true)}));
  head.appendChild(btn('👁','Ein-/ausblenden',function(){c.h=c.h?0:1;row.classList.toggle('off',!!c.h);numberAll();setDirty(true)}));
  row.appendChild(head);
  return row;
}
/* Nummern wie am Fernseher (ohne Favoriten): sichtbare Sender sichtbarer Gruppen der Reihe nach */
function numberAll(){
  var n=0;
  DATA.groups.forEach(function(g){
    var row=document.querySelector('#groups > .row[data-i="'+g.i+'"]');
    var nums=row?row.querySelectorAll('.chs .num'):[];
    g.c.forEach(function(c,k){var t='';if(!g.h&&!c.h){n++;t=n}if(nums[k])nums[k].textContent=t});
  });
}
/* Ziehen zum Sortieren – funktioniert mit Finger und Maus */
function sortable(list,onDone){
  list.addEventListener('pointerdown',function(e){
    var h=e.target.closest('.h');if(!h)return;
    var row=h.closest('.row');if(!row||row.parentNode!==list)return;
    e.preventDefault();e.stopPropagation();
    var r=row.getBoundingClientRect(),offY=e.clientY-r.top,lastY=e.clientY;
    var ph=el('div','ph');ph.style.height=r.height+'px';row.after(ph);
    row.classList.add('drag');row.style.width=r.width+'px';row.style.left=r.left+'px';row.style.top=r.top+'px';
    try{h.setPointerCapture(e.pointerId)}catch(_){}
    var timer=setInterval(function(){var y=lastY;if(y<90)window.scrollBy(0,-14);else if(y>innerHeight-110)window.scrollBy(0,14);place(y)},30);
    function place(y){
      var sibs=[].filter.call(list.children,function(x){return x!==row&&x!==ph});
      var lo=0,hi=sibs.length;
      while(lo<hi){var mid=(lo+hi)>>1,b=sibs[mid].getBoundingClientRect();if(y<b.top+b.height/2)hi=mid;else lo=mid+1}
      if(lo<sibs.length){if(sibs[lo]!==ph.nextSibling)list.insertBefore(ph,sibs[lo])}else if(list.lastChild!==ph)list.appendChild(ph);
    }
    function move(ev){lastY=ev.clientY;row.style.top=(ev.clientY-offY)+'px';place(ev.clientY)}
    function up(){
      clearInterval(timer);
      h.removeEventListener('pointermove',move);h.removeEventListener('pointerup',up);h.removeEventListener('pointercancel',up);
      ph.replaceWith(row);row.classList.remove('drag');row.style.width=row.style.left=row.style.top='';
      onDone();
    }
    h.addEventListener('pointermove',move);h.addEventListener('pointerup',up);h.addEventListener('pointercancel',up);
  });
}
sortable(${'$'}('groups'),function(){
  DATA.groups=[].map.call(${'$'}('groups').children,function(r){return r._g});numberAll();setDirty(true);
});
function save(){
  ${'$'}('save').disabled=true;msg('Speichere …');
  var body={v:DATA.v,groups:DATA.groups.map(function(g){return{i:g.i,n:g.n,h:g.h,c:g.c.map(function(c){return[c.i,c.h]})}})};
  api('/api/save',body).then(function(){return load()}).then(function(){msg('Gespeichert ✓ – am Fernseher übernommen')})
    .catch(function(e){msg(e.message,true);${'$'}('save').disabled=false});
}
window.addEventListener('beforeunload',function(e){if(dirty){e.preventDefault();e.returnValue=''}});
try{var qc=new URLSearchParams(location.search).get('c');if(qc){CODE=qc;${'$'}('code').value=qc}}catch(e){}
if(CODE){load().catch(function(){CODE=''})}
</script></body></html>
"""
    }
}
