package de.aze.iptv.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselt gespeicherte Zugangsdaten mit einem Schlüssel aus dem Android-Schlüsselspeicher.
 * Der Schlüssel verlässt das Gerät nie. Ab Android 6; auf älteren Geräten bleibt es beim privaten App-Speicher.
 */
object Secure {

    private const val ALIAS = "aze_iptv_secret"
    private const val PREFIX = "enc1:"

    @Volatile private var cached: SecretKey? = null

    private fun key(): SecretKey? {
        if (Build.VERSION.SDK_INT < 23) return null
        cached?.let { return it }
        return runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
                val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                gen.init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                gen.generateKey()
            }
        }.getOrNull().also { cached = it }
    }

    // ---- Schnelle Verschlüsselung ----
    // Der Android-Schlüsselspeicher ist langsam (jeder Aufruf geht an einen Systemdienst; große Daten dauern
    // Sekunden). Deshalb: einmal einen eigenen Datenschlüssel erzeugen, ihn mit dem Schlüsselspeicher geschützt
    // ablegen und die Daten damit im App-Speicher verschlüsseln – genauso sicher, aber ohne Hänger.
    private const val PREFIX2 = "enc2:"
    @Volatile private var prefs: android.content.SharedPreferences? = null
    @Volatile private var dataKey: SecretKey? = null

    fun init(context: android.content.Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("aze_k", android.content.Context.MODE_PRIVATE)
    }

    private fun dataKey(): SecretKey? {
        dataKey?.let { return it }
        synchronized(this) {
            dataKey?.let { return it }
            val p = prefs ?: return null
            if (key() == null) return null
            val bytes = p.getString("dek", null)?.let { stored ->
                decryptKeystore(stored)?.let { Base64.decode(it, Base64.NO_WRAP) }
            } ?: run {
                val b = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val enc = encryptKeystore(Base64.encodeToString(b, Base64.NO_WRAP)) ?: return null
                p.edit().putString("dek", enc).commit()
                b
            }
            return javax.crypto.spec.SecretKeySpec(bytes, "AES").also { dataKey = it }
        }
    }

    /** Platzhalter, wenn Verschlüsseln fehlschlägt: lieber nichts speichern als Klartext. */
    private const val FAILED = "encfail:"

    fun encrypt(plain: String): String {
        // Ohne Schlüsselspeicher (Android 5.x) bleibt es beim privaten App-Speicher; sonst nie Klartext
        if (Build.VERSION.SDK_INT < 23) return plain
        val k = dataKey() ?: return encryptKeystore(plain) ?: FAILED
        return runCatching {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
            PREFIX2 + Base64.encodeToString(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }.getOrDefault(FAILED)
    }

    fun decrypt(stored: String?): String? {
        if (stored == null || stored == FAILED) return null
        if (stored.startsWith(PREFIX2)) {
            val k = dataKey() ?: return null
            return runCatching {
                val data = Base64.decode(stored.removePrefix(PREFIX2), Base64.NO_WRAP)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, data, 0, 12))
                String(c.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
            }.getOrNull()
        }
        return decryptKeystore(stored)
    }

    /** Direkt mit dem Schlüsselspeicher (nur für den Datenschlüssel und ältere gespeicherte Werte). */
    private fun encryptKeystore(plain: String): String? {
        val k = key() ?: return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, k)
            val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decryptKeystore(stored: String?): String? {
        if (stored == null || !stored.startsWith(PREFIX)) return stored
        val k = key() ?: return null
        return runCatching {
            val data = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, data, 0, 12))
            String(c.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    /** PIN nie im Klartext speichern: nur Prüfsumme mit Salz. */
    fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(md.digest((salt + ":" + pin).toByteArray()), Base64.NO_WRAP)
    }

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    /** Zeigt von einem Link nur Anfang und Server, z. B. "http://server.com/…". */
    fun shortUrl(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val u = URI(url.trim())
            val host = u.host ?: return@runCatching url.take(12) + "…"
            val port = if (u.port > 0) ":${u.port}" else ""
            "${u.scheme ?: "http"}://$host$port/…"
        }.getOrDefault(url.take(12) + "…")
    }
}
