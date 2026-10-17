package com.heysig.app

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** Injectable boundary for silent acceptance checks; production has one runtime and speaker adapter. */
internal interface CommandTransport {
    fun inspect(allowLocked: Boolean)
    fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean)
}
internal class SpeakerCommandTransport(context: Context) : CommandTransport {
    private val speaker=SpeakerPlayback(context)
    override fun inspect(allowLocked: Boolean) { speaker.inspect(allowLocked) }
    override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) { speaker.play(pcm,rate,cancelled,allowLocked) }
}
