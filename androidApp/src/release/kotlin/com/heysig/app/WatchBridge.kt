package com.heysig.app

import android.content.Context
import org.json.JSONObject

/** Variant seam: the production artifact has no loopback listener or ADB bootstrap. */
class WatchBridge(context: Context, dispatch: (JSONObject, (JSONObject) -> Unit) -> Unit) : AutoCloseable {
    init { error("Development relay is unavailable in release builds") }
    override fun close() = Unit
}
