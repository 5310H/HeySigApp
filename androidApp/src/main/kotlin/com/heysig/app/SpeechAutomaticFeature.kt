package com.heysig.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import com.heysig.control.Program
import com.heysig.noise.*
import org.json.JSONObject

/** UI owns explicit live consent and saved role choices. No calibration or learning requirement. */
class SpeechAutomaticFeature(private val activity:Activity,private val programs:()->List<Program>,
    private val settings:()->NoiseSettings,private val safety:()->NoiseSafety,private val prepare:()->(() -> Unit),
    private val prefs:android.content.SharedPreferences=activity.getSharedPreferences("heysig-masking-v1",0)) {
    private val handler=Handler(Looper.getMainLooper())
    private var status:TextView?=null
    private var toggle:Button?=null
    private var saveButton:Button?=null
    private var message:String?=null
    private val selectors=mutableListOf<Spinner>()
    private var readChoices:(()->MaskingChoices)?=null
    private var renderedFitting:String?=null
    fun offStatus():String?=message ?: SpeechAutomaticService.lastStatus.takeIf { it.isNotBlank() }
    private fun signature()=programs().sortedBy { it.slot }.joinToString("|") { "${it.slot}:${it.name}" }
    private fun savedChoices():MaskingChoices {
        val raw=prefs.getString("choices",null)
        if(raw!=null) return runCatching {
            val json=JSONObject(raw);check(json.getInt("version")==1 && json.getString("fitting")==signature())
            fun slot(key:String)=if(json.isNull(key)) null else json.getInt(key)
            MaskingChoices(slot("everyday"),slot("noisy"),slot("veryNoisy"))
        }.getOrElse { MaskingChoices(null,null,null) }
        val old=settings().programs
        return MaskingChoices(old[0],old[3],old[5])
    }
    private val refresh=object:Runnable {
        override fun run() { update();handler.postDelayed(this,1000) }
    }
    fun detach() { handler.removeCallbacksAndMessages(null);status=null;toggle=null;saveButton=null;readChoices=null;renderedFitting=null;selectors.clear() }
    private fun update() {
        val on=SpeechAutomaticService.active
        toggle?.apply { text=if(on) "On" else "Off";contentDescription="Speech-aware Automatic ${text}. Tap to turn ${if(on) "Off" else "On"}";PhoneStyle.action(this,if(on) "noiseOn" else "noiseOff") }
        selectors.forEach { it.isEnabled=!on }
        saveButton?.isEnabled=!on
        status?.text=SpeechAutomaticService.running?.summary() ?: message ?: SpeechAutomaticService.lastStatus.ifBlank { "Automatic Off. Microphone stopped." }
    }
    fun start() {
        message=null
        check(readChoices==null || renderedFitting==signature()) { "Your program setup changed. Reopen Choose programs and review the choices." }
        check(safety().let { it.foreground && it.unlocked && it.audioFree }) { "Keep HeySig open and unlocked, and wait until speech or the current command finishes." }
        check(!activity.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { "Unlock the phone to start Automatic" }
        if(!prefs.getBoolean("disclosureV1",false)) {
            AlertDialog.Builder(activity).setTitle("Turn on speech-aware Automatic?")
                .setMessage("Automatic sends tones to select your chosen hearing-aid programs. It uses Everyday for sustained quiet or speech that stands out, Noisy for partly masked speech, and Very Noisy for heavily masked speech. Uncertain sound or an unassigned choice keeps the current program.\n\nNo calibration, examples or learning period is needed. This experimental model can make mistakes. It cannot identify the person you want to hear or measure how clearly you understand words. Other listening features stop first.\n\nThe microphone checks for three seconds, then pauses for your saved wait time (at least ten seconds). Audio is processed on this phone in memory, then discarded. No recordings, transcripts or listening history are saved or uploaded. Only your program choices and consent are saved. Existing fitting and six sound-level choices stay unchanged.\n\nYou can lock the screen. A HeySig notification has an Off button. Tap Off here or in that notification to stop. Automatic stops after eight hours, on manual control or Therapy, if permissions/notifications are removed, or if the app is stopped. It never restarts by itself.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("disclosureV1",true).commit()) runCatching { start() }.onFailure { message=it.message;update() }
                }.show();return
        }
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402);message="Allow microphone access, then tap Off again.";update();return
        }
        if(Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),403);message="Allow notifications for the visible Off control, then tap Off again.";update();return
        }
        val selected=saveChoices()
        val rollback=prepare()
        try { SpeechAutomaticService.launch(activity,settings(),selected,signature()) } catch(e:Exception) { rollback();throw e }
        update()
    }
    private fun saveChoices():MaskingChoices {
        check(readChoices==null || renderedFitting==signature()) { "Program setup changed; reopen Choose programs and review." }
        val selected=readChoices?.invoke() ?: savedChoices()
        val choices=JSONObject().put("version",1).put("fitting",signature()).put("everyday",selected.everyday ?: JSONObject.NULL)
            .put("noisy",selected.noisy ?: JSONObject.NULL).put("veryNoisy",selected.veryNoisy ?: JSONObject.NULL)
        check(prefs.edit().putString("choices",choices.toString()).commit()) { "Could not save speech program choices" }
        return selected
    }
    fun render(content:LinearLayout,index:Int?=null) {
        renderedFitting=signature()
        val card=PhoneStyle.card(activity,content)
        if(index!=null) { content.removeView(card);content.addView(card,index) }
        PhoneStyle.heading(activity,card,"Speech-aware Automatic")
        fun line(value:String)=TextView(activity).also { it.text=value;PhoneStyle.text(it);it.setPadding(0,8,0,16);card.addView(it) }
        line("Choose the programs Automatic can send to your hearing aids. No setup recordings or waiting to learn.")
        val available=programs().filterNot { isTherapyProgramName(it.name) }
        val saved=savedChoices()
        fun choice(label:String,slot:Int?):Spinner {
            line(label)
            return Spinner(activity).also { spinner ->
                spinner.contentDescription=label
                spinner.adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,listOf("Keep current program")+available.map { "${it.name} (program ${it.slot})" })
                spinner.setSelection(available.indexOfFirst { it.slot==slot }+1);spinner.minimumHeight=PhoneStyle.dp(activity,48);card.addView(spinner)
                selectors.add(spinner)
            }
        }
        val everyday=choice("Everyday · quiet or speech that stands out",saved.everyday)
        val noisy=choice("Noisy · speech partly masked",saved.noisy)
        val very=choice("Very Noisy · speech heavily masked",saved.veryNoisy)
        readChoices={ fun slot(s:Spinner)=available.getOrNull(s.selectedItemPosition-1)?.slot
            MaskingChoices(slot(everyday),slot(noisy),slot(very)) }
        saveButton=Button(activity).also { button ->
            button.text="Save speech program choices";PhoneStyle.action(button);card.addView(button)
            button.setOnClickListener { runCatching { saveChoices();message="Your speech program choices are saved. You can go back to Noise and turn Automatic on." }.onFailure { message=it.message };update() }
        }
        line("Starting choices come from saved sound levels 1, 4 and 6. All six original assignments stay unchanged. Tap Save speech program choices before going back. Turning Automatic On here also saves your choices.")
        toggle=Button(activity).also { button -> card.addView(button);button.setOnClickListener {
            message=null
            if(SpeechAutomaticService.active) SpeechAutomaticService.cancel()
            else runCatching { start() }.onFailure { message=it.message }
            update()
        } }
        status=line("").apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        line("Uses your saved repeated-check count (at least two checks) and cooldown. Quiet or clear speech must also agree before returning to Everyday. No chosen program, uncertain sound, manual control or Therapy means hold. Sound type and masking are separate.")
        line("You may lock the screen while Automatic is On. Keep the phone where your aids can hear its tones. Use the notification Off button to stop. It stops after eight hours. This experimental model can make mistakes; choosing a program yourself stops Automatic.")
        handler.removeCallbacksAndMessages(null);handler.post(refresh);update()
    }
}
