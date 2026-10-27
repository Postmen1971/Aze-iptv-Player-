package de.aze.iptv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

enum class ProxyType(val label: String) { OFF("Aus"), HTTP("HTTP"), SOCKS("SOCKS5") }

data class ProxySettings(
    val type: ProxyType = ProxyType.OFF,
    val host: String = "",
    val port: Int = 0,
    val user: String = "",
    val pass: String = "",
) {
    val active: Boolean get() = type != ProxyType.OFF && host.isNotBlank() && port in 1..65535
}

object Network {

    private val systemSelector: ProxySelector? = ProxySelector.getDefault()

    /**
     * Leitet alle Verbindungen der App (Playlist, Streams, Updates) über den eingestellten Proxy.
     * Beide nutzen die Java-Netzwerkschicht, daher wirkt das für die ganze App.
     */
    fun applyProxy(settings: ProxySettings) {
        if (!settings.active) {
            ProxySelector.setDefault(systemSelector)
            Authenticator.setDefault(null)
            return
        }
        val proxy = Proxy(
            if (settings.type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP,
            InetSocketAddress.createUnresolved(settings.host.trim(), settings.port),
        )
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOf(proxy)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        })
        if (settings.user.isNotBlank()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? =
                    if (requestorType == RequestorType.PROXY) PasswordAuthentication(settings.user, settings.pass.toCharArray()) else null
            })
        } else {
            Authenticator.setDefault(null)
        }
    }

    /** true, wenn auf dem Gerät gerade ein VPN aktiv ist. */
    fun vpnActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 23) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .any { it.isUp && (it.name.startsWith("tun") || it.name.startsWith("ppp")) }
        }.getOrDefault(false)
    }
}
