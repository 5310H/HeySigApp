package com.heysig.app

import android.content.Context
import com.heysig.control.*

/** Isolated preferences only: no microphone, user fitting changes or command playback. */
object PersonalPhraseAcceptance {
    fun run(context: Context) {
        val prefs = context.getSharedPreferences("acceptance-isolated-personal-phrases",0)
        check(prefs.edit().clear().commit())
        val fit = Fitting(12,12,12,listOf(Program(1,"Universal",TinnitusCapability.SUPPORTED),Program(5,"TV",TinnitusCapability.UNKNOWN)))
        val expected = AppIntent.Control(ManualRequest.SelectProgram(5))
        fun teach(store: PersonalPhraseStore) {
            check(store.manage("learn phrase TV time means program TV",fit,"phone")!!.contains("Remember"))
            check(store.resolve("TV time",fit) is AppIntent.Unsupported)
            check(store.manage("remember that correction",fit,"phone")!!.contains("Remembered"))
        }
        try {
            var store = PersonalPhraseStore(prefs)
            for(phrase in listOf("stop listening", "voice prompts off", "noise sample", "learned phrases off"))
                check(store.manage("learn phrase $phrase means program TV",fit,"phone")!!.contains("reserved"))
            teach(store)
            store = PersonalPhraseStore(prefs)
            check(store.resolve("TV time",fit) == expected)
            check(store.identity("TV time",fit) == expected)
            check(listOf("TV time","program Universal").map { store.identity(it,fit) }.distinct().size == 2)
            check(store.resolve("TV time and Universal",fit) is AppIntent.Unsupported)
            check(store.enable(false))
            check(store.resolve("TV time",fit) is AppIntent.Unsupported)
            store = PersonalPhraseStore(prefs)
            check(!store.enabled())
            check(store.enable(true)); check(store.resolve("TV time",fit) == expected)
            val changed = fit.copy(controlAddress=13)
            store.setupChanged(changed)
            check(store.resolve("TV time",changed) is AppIntent.Unsupported)
            store.setupChanged(fit)
            check(store.resolve("TV time",fit) is AppIntent.Unsupported)
            check(store.manage("list my phrases",fit,"phone")!!.contains("inactive"))
            check(store.manage("forget phrase TV time",fit,"phone")!!.contains("Forgot"))
            teach(store)
            check(store.manage("reset learned phrases",fit,"watch:1")!!.contains("Delete"))
            check(store.manage("confirm reset learned phrases",fit,"phone")!!.contains("device"))
            check(store.manage("cancel",fit,"watch:1") == null)
            check(store.resolve("TV time",fit) == expected)
            store.manage("reset learned phrases",fit,"phone")
            check(store.manage("confirm reset learned phrases",fit,"phone")!!.contains("deleted"))
            check(PersonalPhraseStore(prefs).resolve("TV time",fit) is AppIntent.Unsupported)
            // Corruption disables personal mappings, preserves bytes and leaves exact commands usable.
            check(prefs.edit().putString("records","broken-json").commit())
            store = PersonalPhraseStore(prefs)
            check(store.resolve("program TV",fit) == expected)
            check(store.manage("program TV",fit,"phone") == null)
            check(prefs.getString("records",null) == "broken-json")
            store.manage("reset learned phrases",fit,"phone")
            check(store.manage("confirm reset learned phrases",fit,"phone")!!.contains("deleted"))
            teach(store)
        } finally { check(prefs.edit().clear().commit()) }
    }
}
