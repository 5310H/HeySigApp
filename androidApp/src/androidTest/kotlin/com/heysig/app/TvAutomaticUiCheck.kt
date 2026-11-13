package com.heysig.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.heysig.control.Program
import com.heysig.control.TinnitusCapability
import com.heysig.noise.*
import org.json.JSONObject

/** Detached test-only UI and isolated preferences: no wearer settings, microphone or aid output. */
object TvAutomaticUiCheck {
    fun verify(screen:Activity):String {
        val prefs=screen.getSharedPreferences("acceptance-tv-choices",0)
        check(prefs.edit().clear().commit())
        var programs=listOf("Universal","Noisy","Very Noisy","Therapy","TV").mapIndexed { i,name ->
            Program(i+1,name,TinnitusCapability.SUPPORTED)
        }
        fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        var feature:SpeechAutomaticFeature?=null
        fun render():LinearLayout {
            feature?.detach()
            val column=LinearLayout(screen)
            feature=SpeechAutomaticFeature(screen,{programs},{NoiseSettings(programs=listOf(1,1,2,2,3,3))},
                {NoiseSafety(foreground=true,unlocked=true,audioFree=true)},{ error("Saving must not start Automatic") },prefs)
            feature!!.render(column)
            return column
        }
        fun save(column:LinearLayout)=views(column).filterIsInstance<Button>().single { it.text=="Save speech program choices" }.performClick()
        try {
            // Old version-1 choices must load without rewriting or enabling TV.
            val signature=programs.joinToString("|") { "${it.slot}:${it.name}" }
            val legacy=JSONObject().put("version",1).put("fitting",signature).put("everyday",1).put("noisy",2).put("veryNoisy",3).toString()
            check(prefs.edit().putString("choices",legacy).commit())
            var column=render()
            check(prefs.getString("choices",null)==legacy)
            var controls=views(column)
            check(controls.filterIsInstance<Switch>().single { it.text=="Watching TV" }.isChecked==false)
            val tv=controls.filterIsInstance<Spinner>().single { it.contentDescription=="TV · your saved television program" }
            check((0 until tv.count).none { tv.getItemAtPosition(it).toString().contains("Therapy") })
            controls.filterIsInstance<Switch>().single { it.text=="Watching TV" }.isChecked=true
            save(column)
            check(prefs.getString("choices",null)==legacy) { "Unassigned TV silently saved or replaced choices" }
            tv.setSelection(4) // Therapy is excluded; TV retains its actual slot 5.
            save(column)
            val saved=JSONObject(prefs.getString("choices",null)!!)
            check(saved.getInt("television")==5 && saved.getBoolean("watchingTv"))
            check(saved.getInt("everyday")==1 && saved.getInt("noisy")==2 && saved.getInt("veryNoisy")==3)
            column=render();controls=views(column)
            check(controls.filterIsInstance<Switch>().single { it.text=="Watching TV" }.isChecked)
            check(controls.filterIsInstance<Spinner>().last().selectedItem.toString().contains("program 5"))
            controls.filterIsInstance<Switch>().single { it.text=="Watching TV" }.isChecked=false
            save(column)
            val normal=JSONObject(prefs.getString("choices",null)!!)
            check(!normal.getBoolean("watchingTv") && normal.getInt("television")==5)
            val before=prefs.getString("choices",null)
            programs=programs.map { if(it.slot==5) it.copy(name="Changed program") else it }
            save(column)
            check(prefs.getString("choices",null)==before) { "Changed fitting silently rebound TV" }
            check(!SpeechAutomaticService.active)
            return "PASS: legacy choices retained; unassigned TV rejected; TV slot 5 saved/restored; Therapy excluded; normal choices retained; stale fitting refused; no capture/playback"
        } finally { feature?.detach();check(prefs.edit().clear().commit()) }
    }
}
