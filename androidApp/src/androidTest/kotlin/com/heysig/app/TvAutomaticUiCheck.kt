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
    fun verify(screen: Activity): String {
        val prefs = screen.getSharedPreferences("acceptance-tv-choices", 0)
        check(prefs.edit().clear().commit())
        var programs = listOf("Universal", "Noisy", "Very Noisy", "Therapy", "TV").mapIndexed { i, name ->
            Program(i + 1, name, TinnitusCapability.SUPPORTED)
        }
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        var feature: SpeechAutomaticFeature? = null
        fun render(): LinearLayout {
            feature?.detach()
            val column = LinearLayout(screen)
            feature = SpeechAutomaticFeature(screen, { programs }, { NoiseSettings(programs = listOf(1, 1, 2, 2, 3, 3)) },
                { NoiseSafety(foreground = true, unlocked = true, audioFree = true) }, { error("Saving must not start Automatic") }, prefs)
            feature!!.render(column)
            return column
        }
        fun save(column: LinearLayout) = views(column).filterIsInstance<Button>()
            .single { it.text == "Save Automatic noise programs" }.performClick()
        try {
            val signature = programs.joinToString("|") { "${it.slot}:${it.name}" }
            val legacy = JSONObject().put("version", 1).put("fitting", signature)
                .put("everyday", 1).put("noisy", 2).put("veryNoisy", 3)
                .put("television", 5).put("watchingTv", true).toString()
            check(prefs.edit().putString("choices", legacy).commit())

            var column = render()
            check(prefs.getString("choices", null) == legacy) { "Rendering rewrote saved choices" }
            var controls = views(column)
            val spinners = controls.filterIsInstance<Spinner>()
            check(spinners.size == 3) { "Expected only Universal, Noisy and Very Noisy choices" }
            check(controls.filterIsInstance<Switch>().none { it.text.toString() == "Watching TV" })
            check(controls.filterIsInstance<TextView>().none { it.text.toString() == "TV · your saved television program" })
            val noise = spinners.single { it.contentDescription == "Noisy · speech partly masked" }
            noise.setSelection(availableIndex(spinners, noise, 5))
            save(column)
            var saved = JSONObject(prefs.getString("choices", null)!!)
            check(saved.getInt("television") == 5 && saved.getBoolean("watchingTv")) {
                "Saving noise choices erased legacy TV settings"
            }
            check(saved.getInt("everyday") == 1 && saved.getInt("noisy") == 5 && saved.getInt("veryNoisy") == 3)

            column = render(); controls = views(column)
            check(controls.filterIsInstance<Spinner>().size == 3)
            check(controls.filterIsInstance<Spinner>().single { it.contentDescription == "Noisy · speech partly masked" }
                .selectedItem.toString().contains("program 5"))
            val before = prefs.getString("choices", null)
            programs = programs.map { if (it.slot == 5) it.copy(name = "Changed program") else it }
            save(column)
            check(prefs.getString("choices", null) == before) { "Changed fitting silently rebound saved choices" }
            check(!SpeechAutomaticService.active)
            return "PASS: only three noise destinations shown; legacy TV settings retained; noise choice saved/restored; stale fitting refused; no capture/playback"
        } finally {
            feature?.detach()
            check(prefs.edit().clear().commit())
        }
    }

    private fun availableIndex(spinners: List<Spinner>, selected: Spinner, slot: Int): Int {
        check(spinners.contains(selected))
        return (0 until selected.count).single { selected.getItemAtPosition(it).toString().endsWith("(program $slot)") }
    }
}
