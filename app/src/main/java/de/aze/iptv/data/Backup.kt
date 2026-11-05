package de.aze.iptv.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Einstellungen sichern / wiederherstellen (Playlists, Favoriten, Senderliste, alle Einstellungen).
 * Die Sicherung wird AUF DEM GERÄT mit einem zufälligen Code verschlüsselt und erst dann auf paste.rs abgelegt –
 * dort liegt nur unlesbarer Datensalat. Ohne den Code (nur auf dem Bildschirm angezeigt) kann niemand etwas damit anfangen.
 * Wiederherstellen: Code eingeben → holen → entschlüsseln → einspielen.
 */
object Backup {
    private val PREFS = listOf("aze", "skortext")
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // ohne 0/O, 1/I – am Fernseher gut abzulesen

    private fun isEnc(v: String) = v.startsWith("enc1:") || v.startsWith("enc2:")

    /** alle Einstellungen als JSON (verschlüsselte Werte vorher entschlüsseln – sie sind an dieses Gerät gebunden) */
    private fun collect(context: Context): JSONObject {
        val root = JSONObject().put("v", 1)
        for (name in PREFS) {
            val o = JSONObject()
            for ((k, v) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                val e = JSONObject()
                when (v) {
                    is String -> if (isEnc(v)) { e.put("t", "x").put("v", Secure.decrypt(v) ?: continue) } else e.put("t", "s").put("v", v)
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is Set<*> -> e.put("t", "S").put("v", org.json.JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                o.put(k, e)
            }
            root.put(name, o)
        }
        return root
    }

    private fun keyFrom(code: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val k = f.generateSecret(PBEKeySpec(code.toCharArray(), salt, 20_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    private fun newCode(): String {
        val r = SecureRandom()
        return (1..10).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    /** Sicherung erstellen und hochladen. Rückgabe: Code zum Abschreiben, z. B. „AB3CD-KLMNP-QRS7T“ (blockierend) */
    fun create(context: Context): String {
        val secret = newCode()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keyFrom(secret, salt), GCMParameterSpec(128, iv))
        val ct = c.doFinal(collect(context).toString().toByteArray(Charsets.UTF_8))
        val blob = "AZE1:" + Base64.encodeToString(salt + iv + ct, Base64.NO_WRAP)
        val link = HangLog.uploadText(blob) // https://paste.rs/XXXXX
        val id = link.trim().substringAfterLast('/').substringBefore('.')
        require(id.length in 3..12 && id.all { it.isLetterOrDigit() }) { "unerwartete Antwort" }
        return "$id-${secret.substring(0, 5)}-${secret.substring(5)}"
    }

    /** Sicherung holen, entschlüsseln und einspielen (blockierend). Danach App neu starten. */
    fun restore(context: Context, code: String) {
        val parts = code.trim().replace(" ", "").split('-').filter { it.isNotEmpty() }
        require(parts.size == 3) { "Code bitte vollständig eingeben (drei Teile mit Bindestrich)" }
        val id = parts[0]
        val secret = (parts[1] + parts[2]).uppercase()
        require(id.all { it.isLetterOrDigit() }) { "Code stimmt nicht" }
        val conn = URL("https://paste.rs/$id").openConnection() as HttpURLConnection
        val blob = try {
            conn.connectTimeout = 15_000; conn.readTimeout = 20_000
            if (conn.responseCode !in 200..299) error("Sicherung nicht gefunden (${conn.responseCode})")
            conn.inputStream.bufferedReader().readText().trim()
        } finally { conn.disconnect() }
        require(blob.startsWith("AZE1:")) { "Das ist keine Aze-IPTV-Sicherung" }
        val data = Base64.decode(blob.removePrefix("AZE1:"), Base64.NO_WRAP)
        val salt = data.copyOfRange(0, 16)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keyFrom(secret, salt), GCMParameterSpec(128, data, 16, 12))
        val plain = runCatching { String(c.doFinal(data, 28, data.size - 28), Charsets.UTF_8) }
            .getOrElse { error("Code stimmt nicht") }
        val root = JSONObject(plain)
        for (name in PREFS) {
            val o = root.optJSONObject(name) ?: continue
            val ed = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                when (e.optString("t")) {
                    "x" -> ed.putString(k, Secure.encrypt(e.getString("v"))) // auf diesem Gerät neu verschlüsseln
                    "s" -> ed.putString(k, e.getString("v"))
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "S" -> ed.putStringSet(k, e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                }
            }
            ed.commit()
        }
    }
}
