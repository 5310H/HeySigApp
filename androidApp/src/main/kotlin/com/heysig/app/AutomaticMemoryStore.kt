package com.heysig.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.SystemClock
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import com.heysig.noise.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicitly reviewed scalar preferences. Only the latest unsaved context lives in memory. */
class AutomaticMemoryStore(context:Context, private val prefs:android.content.SharedPreferences=context.getSharedPreferences("heysig-automatic-memory-v1",0)) {
    fun enabled()=prefs.getBoolean("enabled",false)
    fun book():AutomaticMemory=runCatching {
        val raw=prefs.getString("experiences",null) ?: return@runCatching AutomaticMemory()
        require(raw.length<=100000)
        val data=JSONObject(raw);require(data.getInt("version")==1)
        val rows=data.getJSONArray("rows");require(rows.length()<=24)
        AutomaticMemory((0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i);val vector=row.getJSONArray("context");require(vector.length()==40)
            AutomaticExperience(row.getString("scope"),AutomaticSoundContext((0 until 40).map { vector.getDouble(it) }),
                AutomaticScene.valueOf(row.getString("scene")),row.getLong("savedAt"))
        })
    }.getOrDefault(AutomaticMemory())
    fun recall(scope:String,value:AutomaticSoundContext?):AutomaticScene? =
        if(enabled() && value!=null) book().recall(scope,value) else null
    internal fun saveReviewed(value:AutomaticMemory) {
        val rows=JSONArray()
        value.experiences.forEach { row -> rows.put(JSONObject().put("scope",row.scope)
            .put("context",JSONArray(row.context.values)).put("scene",row.scene.name).put("savedAt",row.savedAt)) }
        check(prefs.edit().putString("experiences",JSONObject().put("version",1).put("rows",rows).toString())
            .putBoolean("enabled",true).commit()) { "Could not save the correction" }
    }
    fun review(activity:Activity,fitting:String,report:(String)->Unit) {
        val last=latest?.takeIf { it.fitting==fitting && SystemClock.elapsedRealtime()-it.at in 0..120000 }
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,8) }
        body.addView(TextView(activity).apply { text="Remembered corrections: "+book().experiences.size+
            ". "+if(enabled()) "Enabled." else "Disabled." })
        val labels=listOf(AutomaticScene.NORMAL,AutomaticScene.NOISY,AutomaticScene.VERY_NOISY)
        val selector=Spinner(activity).apply {
            contentDescription="What should Automatic have recognized?"
            adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,labels.map { it.label })
            last?.scene?.let { setSelection(labels.indexOf(it).coerceAtLeast(0)) }
        }
        body.addView(TextView(activity).apply { text=if(last==null)
            "Turn Automatic on or run the ten-second check first, then review its latest situation here within two minutes."
            else "Last situation: "+last.scene.label+". Choose the noise category it should recognize next time. Remember saves a compact sound summary and your category. No recording or transcript is saved. Up to 24 corrections are kept. Strong noise, confirmation, cooldown and manual/Therapy priority still apply." })
        body.addView(selector)
        val dialog=AlertDialog.Builder(activity).setTitle("Teach Automatic").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel",null)
            .setNeutralButton(if(enabled()) "Disable memories" else "Enable memories") { _,_ ->
                if(prefs.edit().putBoolean("enabled",!enabled()).commit()) report("Remembered corrections "+if(enabled()) "enabled." else "disabled.")
            }.setPositiveButton("Remember",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=last!=null
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching {
                    val sample=checkNotNull(last)
                    check(sample===latest && sample.fitting==fitting && SystemClock.elapsedRealtime()-sample.at in 0..120000) { "The sound check changed or expired. Review a fresh check." }
                    saveReviewed(book().remember(AutomaticExperience(sample.scope,sample.context,labels[selector.selectedItemPosition],System.currentTimeMillis())))
                    report("Remembered "+labels[selector.selectedItemPosition].label+". Turn Automatic on when ready.")
                    dialog.dismiss()
                }.onFailure { report(it.message ?: "Could not remember the correction") }
            }
        }
        dialog.show()
    }
    fun forget(activity:Activity,report:(String)->Unit) {
        AlertDialog.Builder(activity).setTitle("Forget Automatic corrections?")
            .setMessage("This removes only the remembered sound summaries and corrections. Program choices and fitting stay saved.")
            .setNegativeButton("Cancel",null).setPositiveButton("Forget") { _,_ ->
                if(prefs.edit().remove("experiences").putBoolean("enabled",false).commit()) report("Automatic corrections forgotten.")
            }.show()
    }
    companion object {
        data class Latest(val fitting:String,val scope:String,val context:AutomaticSoundContext,val scene:AutomaticScene,val at:Long)
        @Volatile var latest:Latest?=null;private set
        fun scope(source:String,fitting:String)=CombinedAutomaticProfile.ID+"|"+source+"|"+fitting
        fun observe(source:String,fitting:String,context:AutomaticSoundContext?,scene:AutomaticScene) {
            latest=context?.let { Latest(fitting,scope(source,fitting),it,scene,SystemClock.elapsedRealtime()) }
        }
    }
}
