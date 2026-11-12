package com.heysig.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.*
import com.heysig.control.*
import com.heysig.noise.isTherapyProgramName

/** Manual saved-program choices; feedback never leaves this Activity's memory. */
class ProgramComparisonFeature(private val activity:Activity,private val fitting:()->Fitting?,
    private val requested:()->Int?,private val ready:()->Boolean,
    private val send:(ManualRequest.SelectProgram,(Boolean)->Unit)->Unit,private val redraw:()->Unit,
    private val review:(String,()->Unit)->Unit={ text,confirm -> AlertDialog.Builder(activity).setTitle("Compare saved programs?")
        .setMessage(text).setNegativeButton("Cancel",null).setPositiveButton("Start comparison") { _,_ -> confirm() }.show();Unit }) {
    private var session:ProgramComparison?=null
    private var pending=false
    private var message=""
    fun clear() { session?.clear();session=null;message="" }
    fun render(content:LinearLayout) {
        fun line(text:String)=TextView(activity).also { it.text=text;PhoneStyle.text(it);content.addView(it) }
        fun button(text:String,enabled:Boolean=true,action:()->Unit)=Button(activity).also {
            it.text=text;it.isEnabled=enabled;PhoneStyle.action(it);content.addView(it);it.setOnClickListener { action() }
        }
        val fit=fitting()
        val available=fit?.programs.orEmpty().filterNot { isTherapyProgramName(it.name) }
        val current=session
        if(current==null) line("Optional manual listening comparison. This screen does not start microphone capture or change Automatic’s model. No recording or learning. Feedback stays in memory and clears when you leave or close this screen.")
        if(current!=null) {
            fun name(slot:Int)=current.fitting.programs.first { it.slot==slot }.name
            val valid=fit==current.fitting && available.any { it.slot==requested() }
            fun request(slot:Int) {
                if(pending || !ready()) { message="Wait until speech or the current command finishes.";redraw();return }
                val command=runCatching { current.target(slot,checkNotNull(fitting())) }.getOrElse { message=it.message ?: "Review setup";redraw();return }
                pending=true
                send(command) { success -> pending=false;message=if(success) "${name(slot)} requested. Confirm reception yourself; this does not prove better understanding." else "Request incomplete. Aid state may be uncertain; check before continuing.";redraw() }
            }
            button("Return to ${name(current.returnTo)}",valid && !pending) { request(current.returnTo) }
            line("Return is a manual request. Closing or leaving does not return automatically. Saved per-program levels apply to every switch.")
            line(message.ifBlank { "Compare the same speaker and background with each program. Stop if listening becomes uncomfortable." })
            button("Try A: ${name(current.first)}",valid && !pending) { request(current.first) }
            button("Try B: ${name(current.second)}",valid && !pending) { request(current.second) }
            if(!valid) line("Program setup or current daytime program changed. Review a new comparison; nothing will be sent here.")
            line("Which worked better? A = ${name(current.first)}; B = ${name(current.second)}. Unknown is a valid answer. These are your observations, not a measured speech score.")
            for((aspect,label) in listOf(ProgramComparison.Aspect.UNDERSTANDING to "Words understood",ProgramComparison.Aspect.EFFORT to "Less listening effort",ProgramComparison.Aspect.COMFORT to "Comfort",ProgramComparison.Aspect.AWARENESS to "Environmental awareness")) {
                line(label)
                val choices=listOf("Choose an answer","A","B","Same","Unsure")
                val spinner=Spinner(activity).also { it.contentDescription=label;it.minimumHeight=PhoneStyle.dp(activity,48);it.adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,choices);content.addView(it) }
                spinner.setSelection(current.answer(aspect)?.ordinal?.plus(1) ?: 0)
                spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) { if(position>0) current.answer(aspect,ProgramComparison.Preference.entries[position-1]) }
                }
            }
            button("Clear comparison and feedback",!pending) { clear();redraw() }
            return
        }
        if(available.size<2) { line("Configure at least two daytime programs first. Therapy programs are excluded.");return }
        fun select(label:String,default:Int):Spinner {
            line(label)
            return Spinner(activity).also { it.contentDescription=label;it.minimumHeight=PhoneStyle.dp(activity,48);it.adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,available.map { p -> p.name });it.setSelection(default);content.addView(it) }
        }
        val a=select("Program A",0);val b=select("Program B",1)
        val back=select("Return program — confirm your usual daytime choice",available.indexOfFirst { it.slot==requested() }.coerceAtLeast(0))
        line(message)
        button("Review comparison",!pending) {
            val now=fitting()
            if(now!=fit || !ready() || available.none { it.slot==requested() }) { message="Choose a daytime program on Home and finish the current operation before comparing.";redraw();return@button }
            val candidate=runCatching { ProgramComparison(checkNotNull(fit),available[a.selectedItemPosition].slot,available[b.selectedItemPosition].slot,available[back.selectedItemPosition].slot) }.getOrElse { message=it.message ?: "Review choices";redraw();return@button }
            review("A: ${available[a.selectedItemPosition].name}\nB: ${available[b.selectedItemPosition].name}\nReturn: ${available[back.selectedItemPosition].name}\n\nNothing is sent until you tap Try or Return. Each tap stops Automatic and uses the existing program control, including any saved volume, balance or tinnitus targets. Fitting and saved targets are not edited. Therapy is excluded. Keep HeySig open; confirm the aid beeps yourself. No feedback is saved or uploaded.",
                {
                    if(fitting()==fit && ready() && available.any { it.slot==requested() }) { session=candidate;message="" } else message="Setup or playback changed; review again."
                    redraw()
                })
        }
    }
}
