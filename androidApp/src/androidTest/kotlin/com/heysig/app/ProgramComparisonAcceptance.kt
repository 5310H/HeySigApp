package com.heysig.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.heysig.control.*

/** Injected confirmation and silent callback only; never uses the owner's runtime. */
object ProgramComparisonAcceptance {
    fun run(activity:Activity) {
        var fit=Fitting(12,12,12,listOf(Program(1,"Universal",TinnitusCapability.UNKNOWN),Program(3,"Noisy",TinnitusCapability.UNKNOWN),Program(2,"Therapy",TinnitusCapability.UNKNOWN)))
        var requested=1;var ready=true;var confirm:(()->Unit)?=null;val sent=mutableListOf<Int>()
        var done:((Boolean)->Unit)?=null
        lateinit var feature:ProgramComparisonFeature
        val content=LinearLayout(activity)
        fun render() { content.removeAllViews();feature.render(content) }
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
        fun click(label:String) { val button=views(content).filterIsInstance<Button>().first { it.text==label };check(button.isEnabled);button.performClick() }
        feature=ProgramComparisonFeature(activity,{fit},{requested},{ready},{ req,callback -> sent.add(req.slot);done=callback },{render()},{ text,callback -> check(text.contains("saved volume") && text.contains("No feedback is saved"));confirm=callback })
        render()
        check(views(content).filterIsInstance<Spinner>().all { spinner -> (0 until spinner.count).none { spinner.getItemAtPosition(it)=="Therapy" } })
        click("Review comparison");check(sent.isEmpty());checkNotNull(confirm).invoke();check(sent.isEmpty())
        click("Try B: Noisy");check(sent==listOf(3));render()
        check(!views(content).filterIsInstance<Button>().first { it.text=="Return to Universal" }.isEnabled)
        requested=3;checkNotNull(done).invoke(true)
        click("Return to Universal");check(sent==listOf(3,1));requested=1;checkNotNull(done).invoke(true)
        ready=false;render();click("Try A: Universal");check(sent==listOf(3,1))
        check(views(content).filterIsInstance<TextView>().any { it.text.toString().contains("Wait until speech") })
        ready=true;fit=fit.copy(controlAddress=13);render()
        check(!views(content).filterIsInstance<Button>().first { it.text=="Return to Universal" }.isEnabled)
        feature.clear();render();check(sent==listOf(3,1));check(views(content).filterIsInstance<Button>().any { it.text=="Review comparison" })
        // A setup change during the explicit review must not arm stale choices.
        click("Review comparison");fit=fit.copy(leftAddress=14);checkNotNull(confirm).invoke()
        check(views(content).filterIsInstance<Button>().none { it.text.toString().startsWith("Try ") })
        check(sent==listOf(3,1));feature.clear()
    }
}
