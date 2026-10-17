package com.heysig.app

import android.os.SystemClock
import java.util.concurrent.Semaphore

/** Serializes this app's AudioRecord adapters; cancellation never waits on the UI thread. */
internal object MicrophoneLease {
    private val semaphore=Semaphore(1)
    fun acquire(cancelled: () -> Boolean): Boolean {
        val deadline=SystemClock.elapsedRealtime()+2000
        while(!cancelled() && SystemClock.elapsedRealtime()<deadline) {
            if(semaphore.tryAcquire()) return true
            Thread.sleep(10)
        }
        return false
    }
    fun release() { semaphore.release() }
}
