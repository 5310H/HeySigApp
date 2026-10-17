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
            text = "This development installation retains its existing ID and watch relay. Normal Wear OS pairing is in the release candidate; it requires matching phone/watch application IDs and signing."
            textSize = 17f
        })
    }
}
