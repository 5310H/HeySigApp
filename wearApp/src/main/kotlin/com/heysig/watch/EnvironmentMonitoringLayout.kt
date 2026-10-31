package com.heysig.watch

import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Actual watch monitoring layout, also measured by isolated round/large-font tests. */
object EnvironmentMonitoringLayout {
    data class Screen(val view: ScrollView, val status: TextView)
    fun create(context: Context, actions: List<Pair<String, () -> Unit>>): Screen {
        val column=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL;WatchStyle.prepareColumn(this)
        }
        fun line(text: String)=TextView(context).apply {
            this.text=text;textSize=16f;setTextColor(Color.WHITE);column.addView(this)
        }
        line("Listening environments").isAccessibilityHeading=true
        val status=line("Connect with HeySig open on your phone").apply {
            accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        for((label,action) in actions) column.addView(Button(context).apply {
            text=label;WatchStyle.polish(this);setOnClickListener { action() }
        })
        line("Voice command returns to the existing Speak flow. Monitoring stops before voice capture. Sampling settings come from Automatic program switching on your phone. Keep both apps visible and unlocked. Leaving this screen stops watch capture. Estimates are experimental.")
        return Screen(ScrollView(context).apply {
            setBackgroundColor(Color.BLACK);isFillViewport=true;addView(column)
        },status)
    }
}
