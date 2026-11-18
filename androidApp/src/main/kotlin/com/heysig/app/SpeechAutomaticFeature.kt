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
    private var sourceSwitch:Switch?=null
    private fun watchSource()=sourceSwitch?.isChecked ?: (prefs.getString("captureSource","phone")=="watch")
    private var message:String?=null
    private val selectors=mutableListOf<Spinner>()
    private var readChoices:(()->MaskingChoices)?=null
    private var renderedFitting:String?=null
    private val memory by lazy { AutomaticMemoryStore(activity) }
    fun teach() { SpeechAutomaticService.cancel();memory.review(activity,signature()) { message=it;update() } }
    fun forgetMemories() { SpeechAutomaticService.cancel();memory.forget(activity) { message=it;update() } }
    fun offStatus():String?=message ?: SpeechAutomaticService.lastStatus.takeIf { it.isNotBlank() }
    fun listeningContext()="Automatic uses noise levels: partly masked speech selects Noisy, heavily masked speech selects Very Noisy, and clear or quiet sound returns to Universal."
    private fun signature()=programs().sortedBy { it.slot }.joinToString("|") { "${it.slot}:${it.name}" }
    private fun savedChoices():MaskingChoices {
        val raw=prefs.getString("choices",null)
        if(raw!=null) return runCatching {
            val json=JSONObject(raw);check(json.getInt("version")==1 && json.getString("fitting")==signature())
            fun slot(key:String)=if(json.isNull(key)) null else json.getInt(key)
            MaskingChoices(slot("everyday"),slot("noisy"),slot("veryNoisy"),slot("television"),json.optBoolean("watchingTv",false))
        }.getOrElse { MaskingChoices(null,null,null) }
        val old=settings().programs
        return MaskingChoices(old[0],old[3],old[5])
    }
    private val refresh=object:Runnable {
        override fun run() { update();handler.postDelayed(this,1000) }
    }
    fun detach() { handler.removeCallbacksAndMessages(null);status=null;toggle=null;saveButton=null;readChoices=null;renderedFitting=null;selectors.clear();sourceSwitch=null }
    private fun update() {
        val on=SpeechAutomaticService.active
        toggle?.apply { text=if(on) "On" else "Off";contentDescription="Automatic noise ${text}. Tap to turn ${if(on) "Off" else "On"}";PhoneStyle.action(this,if(on) "noiseOn" else "noiseOff") }
        selectors.forEach { it.isEnabled=!on }
        saveButton?.isEnabled=!on
        sourceSwitch?.isEnabled=!on
        status?.text=SpeechAutomaticService.running?.summary()
            ?: if(on) "Speech-aware Automatic starting…" else message ?: SpeechAutomaticService.lastStatus.ifBlank { "Automatic Off. Microphone stopped." }
    }
    fun start() {
        message=null
        check(readChoices==null || renderedFitting==signature()) { "Your program setup changed. Reopen Choose programs and review the choices." }
        check(safety().let { it.foreground && it.unlocked && it.audioFree }) { "Keep HeySig open and unlocked, and wait until speech or the current command finishes." }
        check(!activity.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { "Unlock the phone to start Automatic" }
        val selected=saveChoices()
        val watch=watchSource()
        val watchPrefs=activity.getSharedPreferences("heysig-companion",0)
        val watchNode=if(watch) watchPrefs.getString("node",null) else null
        if(watch) {
            check(watchNode!=null && watchPrefs.getBoolean("foregroundEnabledV1",false)) { "Use Watch connection to select and enable your paired watch first" }
            if(!prefs.getBoolean("watchNoiseDisclosureV3",false)) {
                AlertDialog.Builder(activity).setTitle("Use the watch microphone for Automatic?")
                    .setMessage("Your selected watch analyzes ten-second microphone captures for speech masking and noise, then pauses at least ten seconds. Watch processing may take up to 30 seconds after capture; repeated checks and cooldown mean changes may take a few minutes. The phone microphone stays idle. Open Automatic noise on the watch and tap On after its explanation.\n\nCompact noise estimates and a 40-number sound summary cross Google's encrypted Wear OS Data Layer, which may route through Google servers. Audio and transcripts are never transferred or saved by this feature. Teach Automatic can save up to 24 summaries and categories only after you review and tap Remember; they stay on the phone and can be disabled or forgotten.\n\nAutomatic can send real program-change tones through the phone, including with the screens locked. Both devices show Off notifications. Off, disconnection, manual control, Therapy, changed settings or eight-hour expiry stops the session. Keep the phone where your aids can hear its tones. The model can make mistakes; battery and wrist-position accuracy require physical checks.")
                    .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                        if(prefs.edit().putBoolean("watchNoiseDisclosureV3",true).commit()) runCatching { start() }.onFailure { message=it.message;update() }
                    }.show();return
            }
        }
        if(!watch && !prefs.getBoolean("noiseDisclosureV2",false)) {
            AlertDialog.Builder(activity).setTitle("Turn on noise Automatic?")
                .setMessage("Automatic estimates whether speech is clear, partly masked or heavily masked, then sends tones for your saved Universal, Noisy or Very Noisy programs. Clear or quiet sound returns to Universal. Uncertain sound and unassigned programs hold the current program. It does not identify TV as a separate situation.\n\nEach capture lasts ten seconds, then pauses for your saved wait time (at least ten seconds) plus processing. It measures three noise and speech-masking sections. Audio is processed in memory and discarded; no recording or transcript is saved or uploaded. Teach Automatic can save up to 24 compact summaries and your categories only after you review and tap Remember. You can disable or forget them. No learning period or calibration is required.\n\nThe phone estimates masking from its microphone; it does not read the hearing aids’ internal noise cancellation. The model can make mistakes and cannot measure your understanding. You may lock the screen. A visible notification offers Off. Off, manual control, Therapy, permission/notification removal, app stop or eight-hour expiry ends the session. It never restarts itself. Other listening features stop first.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("noiseDisclosureV2",true).commit()) runCatching { start() }.onFailure { message=it.message;update() }
                }.show();return
        }
        if(!watch && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402);message="Allow microphone access, then tap Off again.";update();return
        }
        if(Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),403);message="Allow notifications for the visible Off control, then tap Off again.";update();return
        }
        val rollback=prepare()
        try { SpeechAutomaticService.launch(activity,settings(),selected,signature(),watchNode=watchNode) } catch(e:Exception) { rollback();throw e }
        update()
    }
    private fun saveChoices():MaskingChoices {
        check(readChoices==null || renderedFitting==signature()) { "Program setup changed; reopen Choose programs and review." }
        val selected=readChoices?.invoke() ?: savedChoices()
        val choices=JSONObject().put("version",1).put("fitting",signature()).put("everyday",selected.everyday ?: JSONObject.NULL)
            .put("noisy",selected.noisy ?: JSONObject.NULL).put("veryNoisy",selected.veryNoisy ?: JSONObject.NULL)
            .put("television",selected.television ?: JSONObject.NULL).put("watchingTv",selected.watchingTv)
        check(prefs.edit().putString("choices",choices.toString()).putString("captureSource",if(watchSource()) "watch" else "phone").commit()) { "Could not save speech program choices" }
        return selected
    }
    fun render(content:LinearLayout,index:Int?=null) {
        renderedFitting=signature()
        val card=PhoneStyle.card(activity,content)
        if(index!=null) { content.removeView(card);content.addView(card,index) }
        PhoneStyle.heading(activity,card,"Automatic noise")
        fun line(value:String)=TextView(activity).also { it.text=value;PhoneStyle.text(it);it.setPadding(0,8,0,16);card.addView(it) }
        line("Choose the programs Automatic can send to your hearing aids. It checks speech and background noise before changing. No setup recordings or waiting to learn.")
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
        val everyday=choice("Universal · quiet or speech that stands out",saved.everyday)
        val noisy=choice("Noisy · speech partly masked",saved.noisy)
        val very=choice("Very Noisy · speech heavily masked",saved.veryNoisy)
        readChoices={ fun slot(s:Spinner)=available.getOrNull(s.selectedItemPosition-1)?.slot
            // Retain legacy TV fields in saved settings without exposing them as Automatic targets.
            MaskingChoices(slot(everyday),slot(noisy),slot(very),saved.television,saved.watchingTv) }
        sourceSwitch=Switch(activity).also { control ->
            control.text="Use watch microphone for Automatic";control.contentDescription=control.text
            control.isChecked=prefs.getString("captureSource","phone")=="watch"
            control.minHeight=PhoneStyle.dp(activity,48);card.addView(control)
        }
        line("Phone microphone is the default. For watch input, select and enable your watch in Watch connection, save this choice, turn Automatic On here, then open Automatic noise on the watch and tap On. Both screens may sleep after the sessions start. Watch analysis is slower than the phone: processing may take up to 30 seconds, and repeated checks plus cooldown can take a few minutes. Individual watch results older than 30 seconds are discarded.")
        saveButton=Button(activity).also { button ->
            button.text="Save Automatic noise programs";PhoneStyle.action(button);card.addView(button)
            button.setOnClickListener { runCatching { saveChoices();message="Your Automatic noise programs are saved. You can go back to Noise and turn Automatic on." }.onFailure { message=it.message };update() }
        }
        line("Starting choices come from saved sound levels 1, 4 and 6. All six original assignments stay unchanged. Existing TV choices remain saved, but Automatic now uses only Universal, Noisy and Very Noisy. Tap Save Automatic noise programs before going back. Turning Automatic On here also saves your choices.")
        toggle=Button(activity).also { button -> card.addView(button);button.setOnClickListener {
            message=null
            if(SpeechAutomaticService.active) SpeechAutomaticService.cancel()
            else runCatching { start() }.onFailure { message=it.message }
            update()
        } }
        status=line("").apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        card.addView(Button(activity).apply { text="Teach Automatic";PhoneStyle.action(this);setOnClickListener { teach() } })
        card.addView(Button(activity).apply { text="Forget remembered corrections";PhoneStyle.action(this);setOnClickListener { forgetMemories() } })
        line("Remembered corrections: "+memory.book().experiences.size+". Teach Automatic lets you review the latest check and remember its noise category. Older TV corrections remain stored but are ignored. Universal return uses two fresh matching checks; noisy changes use your saved repeated-check count (at least two), after cooldown. Manual control and Therapy take priority.")
        line("You may lock the screen while Automatic is On. Keep the phone where your aids can hear its tones. Use the notification Off button to stop. It stops after eight hours. This experimental model can make mistakes; choosing a program yourself stops Automatic.")
        handler.removeCallbacksAndMessages(null);handler.post(refresh);update()
    }
}
