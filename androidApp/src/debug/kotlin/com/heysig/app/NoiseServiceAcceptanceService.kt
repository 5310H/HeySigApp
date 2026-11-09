package com.heysig.app

import java.util.concurrent.atomic.AtomicBoolean

/** Development-only lifecycle harness: isolated fitting and storage, never speaker playback. */
class NoiseServiceAcceptanceService : AutomaticNoiseService() {
    override val runtime by lazy {
        val prefs=getSharedPreferences("acceptance-noise-service-core",0)
        check(prefs.edit().clear().putString("fitting","""{"control":12,"left":12,"right":12,"name1":"Quiet","tin1":"SUPPORTED","name2":"Noisy","tin2":"SUPPORTED"}""").putString("requested","""{"program":1}""").commit())
        val sleep=getSharedPreferences("acceptance-noise-service-sleep",0).also { check(it.edit().clear().commit()) }
        val phrases=getSharedPreferences("acceptance-noise-service-phrases",0).also { check(it.edit().clear().commit()) }
        ManualRuntime.isolated(prefs,object: CommandTransport {
            override fun inspect(allowLocked: Boolean) { check(allowLocked) }
            override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) {
                // Deliberately no speaker adapter. A regression cannot reach hearing aids.
                check(allowLocked && pcm.isNotEmpty())
            }
        },sleep,phrases)
    }
}
