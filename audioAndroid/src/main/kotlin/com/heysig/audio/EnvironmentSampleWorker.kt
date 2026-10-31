package com.heysig.audio

import com.heysig.noise.EnvironmentClassification
import java.util.concurrent.atomic.AtomicBoolean

/** Android adapter worker. Dependencies also permit command-free cancellation/race tests. */
class EnvironmentSampleWorker(
    private val allowed: () -> Boolean,
    private val capture: (Int, AtomicBoolean) -> ShortArray,
    private val classify: (ShortArray, () -> Boolean) -> EnvironmentClassification,
    private val clock: () -> Long
) {
    private var active: AtomicBoolean? = null
    @Synchronized fun stop() { active?.set(true) }
    @Synchronized fun busy() = active != null
    @Synchronized fun sample(seconds: Int, done: (EnvironmentClassification?, String?, Long) -> Unit): Boolean {
        require(seconds in listOf(3,5,10))
        if(active != null) return false
        val cancel=AtomicBoolean(false)
        active=cancel
        Thread({
            var pcm: ShortArray?=null
            var result: EnvironmentClassification?=null
            var error: String?=null
            var capturedAt=clock()
            try {
                check(!cancel.get() && allowed()) { "Capture access ended; sample discarded" }
                pcm=capture(seconds,cancel)
                capturedAt=clock()
                check(!cancel.get() && allowed()) { "Capture access ended; sample discarded" }
                result=classify(pcm) { cancel.get() || !allowed() }
                check(!cancel.get() && allowed()) { "Capture access ended; sample discarded" }
            } catch(e: Exception) {
                result=null;error=e.message ?: "Microphone/model unavailable"
            } catch(e: LinkageError) {
                result=null;error="Audio model runtime unavailable"
            } finally {
                pcm?.fill(0)
                synchronized(this) { if(active===cancel) active=null }
            }
            // Adapters additionally reject callbacks belonging to a stopped session.
            if(!cancel.get()) done(result,error,capturedAt)
        },"heysig-environment").start()
        return true
    }
}
