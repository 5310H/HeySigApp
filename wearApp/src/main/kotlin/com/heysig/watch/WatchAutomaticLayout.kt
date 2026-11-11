package com.heysig.watch

import android.content.Context
import android.graphics.Color
import android.widget.*

/** Actual Automatic controls, also measurable in isolated round/large-font contexts. */
object WatchAutomaticLayout {
    data class Screen(val view:ScrollView,val status:TextView,val toggle:Button)
    fun create(context:Context,build:String,onToggle:()->Unit,onBack:()->Unit):Screen {
        val column=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;WatchStyle.prepareColumn(this) }
        fun line(text:String)=TextView(context).also { it.text=text;it.textSize=16f;it.setTextColor(Color.WHITE);it.setPadding(0,8,0,8);column.addView(it) }
        line("Watch Automatic").isAccessibilityHeading=true
        val toggle=Button(context).also { it.text="On";WatchStyle.polish(it);column.addView(it);it.setOnClickListener { onToggle() } }
        val status=line("Watch Automatic Off. Microphone stopped.").apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        column.addView(Button(context).apply { text="Back";WatchStyle.polish(this);setOnClickListener { onBack() } })
        line("First on the phone: Noise → Choose programs → Use watch microphone → Save. Turn Automatic On on the phone, then tap On here. Select your phone once in More → Connect phone.")
        line("The watch analyzes speech and background sound locally. The phone applies your Universal, Noisy and Very Noisy choices and sends the aid tones. Keep the phone where your aids can hear it. Both screens may sleep. Off on either device stops Automatic.")
        line("Experimental estimates can be wrong. Wrist position, battery life and listening benefit need real-world testing. No setup recordings or learning period.")
        line(build)
        return Screen(ScrollView(context).apply { setBackgroundColor(Color.BLACK);isFillViewport=true;addView(column) },status,toggle)
    }
}
