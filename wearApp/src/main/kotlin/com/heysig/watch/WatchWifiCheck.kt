package com.heysig.watch

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.*
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** User-requested, bounded passive network observation. No scan, SSID, IP, history or radio change. */
class WatchWifiCheck(private val activity: Activity, private val checkPhone: () -> Unit) {
    private val main=Handler(Looper.getMainLooper())
    private val manager=activity.getSystemService(ConnectivityManager::class.java)
    private val networks=mutableMapOf<Network,Boolean>()
    private var registered=false
    private var closed=false
    private var observed=false
    private val dialog=AlertDialog.Builder(activity).setTitle("Watch Wi-Fi")
        .setMessage("Checking current Wi-Fi connections…")
        .setPositiveButton("Check phone link") { _,_ -> checkPhone() }
        .setNeutralButton("Wi-Fi settings") { _,_ ->
            try { activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            catch(e: Exception) { AlertDialog.Builder(activity).setMessage("Open the watch's Settings → Connectivity → Wi-Fi.").setPositiveButton("Close",null).show() }
        }
        .setNegativeButton("Close",null).create()
    private val callback=object: ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            main.post {
                if(closed) return@post
                observed=true
                if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) networks[network]=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                else networks.remove(network)
                render()
            }
        }
        override fun onLost(network: Network) { main.post { if(!closed) { networks.remove(network);render() } } }
    }
    private val finish=Runnable { if(!closed) { observed=true;unregister();render() } }
    private fun render() {
        if(closed || !dialog.isShowing) return
        val wifi=when {
            networks.values.any { it } -> "Wi-Fi connected. Android reports validated internet access."
            networks.isNotEmpty() -> "Wi-Fi connected. Internet access is not validated."
            !observed -> "Checking current Wi-Fi connections…"
            else -> "No active Wi-Fi connection observed. The watch may be using its phone/Bluetooth connection."
        }
        dialog.setMessage("$wifi\n\nWi-Fi and the HeySig phone link are separate. Check phone link requests a fresh status reply only; it sends no hearing-aid command. This check does not turn Wi-Fi on or collect network names.")
    }
    fun show(): WatchWifiCheck {
        dialog.setOnDismissListener { close() };dialog.show()
        try {
            manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback)
            registered=true;main.postDelayed(finish,5_000)
        } catch(e: Exception) { observed=true;dialog.setMessage("Wi-Fi status unavailable. Open Wi-Fi settings on the watch. No settings were changed.") }
        return this
    }
    private fun unregister() { if(registered) { registered=false;runCatching { manager.unregisterNetworkCallback(callback) } } }
    fun close() { if(closed) return;closed=true;main.removeCallbacks(finish);unregister();dialog.dismiss() }
    companion object {
        fun requested(text: String)=text.trim().lowercase().replace(Regex("\\s+")," ").trimEnd('.','!','?') in listOf("check wi-fi","check wifi","check watch wifi","check watch wi-fi","wifi status","wi-fi status")
    }
}
