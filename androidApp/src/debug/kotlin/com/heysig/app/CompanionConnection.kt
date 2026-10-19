package com.heysig.app

import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Preserve the existing development IDs and relay; Data Layer needs the shared release identity. */
class CompanionConnection(activity: Activity, dispatch: (String, JSONObject, (JSONObject) -> Unit) -> Unit,
    lookup: (String, String) -> JSONObject?, disconnected: () -> Unit, report: (String) -> Unit) : AutoCloseable {
    val enabled = false
    fun accepts(node: String) = false
    fun resume() = Unit
    fun pause() = Unit
    override fun close() = Unit
    fun render(layout: LinearLayout) {
        layout.addView(TextView(layout.context).apply {
            text = "Phone-to-watch setup is unavailable in this development installation. It connects the development watch through the Chromebook Watch Wi-Fi relay. For a direct Pixel/watch connection, install the matching HeySig phone and watch test candidates on both devices. They install separately and do not copy your existing fitting. In the phone candidate choose Find paired watches, select and enable your watch; then choose Connect phone on the watch. Keep the phone app open and unlocked, or explicitly start its hands-free session."
            textSize = 17f
        })
    }
}
