package de.aze.iptv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Autostart: startet den Player nach dem Einschalten des Geräts, wenn das in den Einstellungen aktiviert ist.
 * Ab Android 10 erlaubt Android das nur, wenn die App "über anderen Apps anzeigen" darf
 * (in den Einstellungen gibt es dafür einen Knopf).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return
        val prefs = context.getSharedPreferences("aze", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("autostart", false)) return
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
