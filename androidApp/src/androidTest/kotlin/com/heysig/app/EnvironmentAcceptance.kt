package com.heysig.app

import android.content.Context
import com.heysig.audio.Yamnet
import com.heysig.noise.*

/** No microphone or transport. Tests actual JNI/model loading and isolated platform storage. */
object EnvironmentAcceptance {
    fun run(context: Context) {
        val prefs=context.getSharedPreferences("acceptance-isolated-environment",0)
        try {
            val settings=EnvironmentSettings(assignments=listOf(EnvironmentProgram(2,"My Restaurant"),null,null,null,null,null),enabled=true)
            check(prefs.edit().clear().putString("settings",EnvironmentSettingsCodec.encode(settings)).commit())
            check(EnvironmentSettingsCodec.decode(checkNotNull(prefs.getString("settings",null)))==settings)
            Yamnet(context).use { model ->
                val silence=model.classify(ShortArray(48000))
                check(silence.confidence.isFinite() && silence.confidence in 0.0..1.0)
            }
            check(!NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED)
        } finally { check(prefs.edit().clear().commit()) }
    }
}
