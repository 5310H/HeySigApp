package com.heysig.app

import android.app.Activity
import android.app.NotificationManager
import android.app.KeyguardManager
import android.media.*
import android.os.Bundle
import android.os.PowerManager
import android.view.ViewGroup
import android.widget.*
import com.heysig.research.*
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground research app; no automation, microphone capture, or accessibility. */
class ProtoActivity : Activity() {
    private val fields = mutableListOf<EditText>()
    private lateinit var status: TextView
    private lateinit var xor: CheckBox
    private lateinit var programState: TextView
    private lateinit var volumeState: TextView
    private val extraStates = mutableMapOf<String, TextView>()
    private val buttons = mutableListOf<Button>()
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Activity-scoped preference filenames change with the class rename.
        val currentPreferences = getPreferences(MODE_PRIVATE)
        for (legacyName in listOf("com.heysig.app.BatteryActivity", "BatteryActivity")) {
            val legacy = getSharedPreferences(legacyName, MODE_PRIVATE)
            val editor = currentPreferences.edit()
            for (key in listOf("lastProgramRequest", "lastVolumeRequest", "last_balance", "last_tinnitus")) {
                if (!currentPreferences.contains(key) && legacy.contains(key)) editor.putInt(key, legacy.getInt(key, 0))
            }
            editor.apply()
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        layout.isFocusableInTouchMode = true
        layout.addView(TextView(this).apply { text = "HeySig acoustic prototype\nUse the configured addresses and profile. Confirm aid response by listening." })
        val names = listOf("Left qualified address", "Right qualified address", "Sample rate Hz", "Center frequency Hz", "Frequency spacing Hz", "Bit duration seconds", "Gain divisor")
        val defaults = listOf("","","44100","15360","512","0.0625","1.2")
        val fieldIds = listOf(R.id.prototype_left_address,R.id.prototype_right_address,R.id.prototype_sample_rate,R.id.prototype_center_frequency,R.id.prototype_frequency_spacing,R.id.prototype_bit_duration,R.id.prototype_gain_divisor)
        names.forEachIndexed { i, name ->
            layout.addView(TextView(this).apply { text = name })
            fields.add(EditText(this).apply { setSingleLine(); setText(defaults[i]); id = fieldIds[i]; layout.addView(this) })
        }
        xor = CheckBox(this).apply { text = "Address-XOR checksum"; isChecked = true; id = R.id.prototype_address_xor }; layout.addView(xor)
        layout.addView(Button(this).apply { text = "Use basic Signia defaults"; setOnClickListener {
            if (!busy.get()) {
                val preset = listOf("0","0","44100","15360","512","0.0625","1.2")
                fields.forEachIndexed { i, field -> field.setText(preset[i]) }; xor.isChecked = true
                status.text = "Basic Signia test preset selected; press one side and listen."
            }
        } })
        for (side in BatterySide.entries) {
            val button = Button(this).apply { text = "Check ${side.name.lowercase()}"; setOnClickListener { start(side) } }
            buttons.add(button); layout.addView(button)
        }
        layout.addView(TextView(this).apply { text = "Direct program experiment\nSlots must match your fitted aid setup. One numeric command; no unmute preamble. Current aid program cannot be read acoustically." })
        val slots = Spinner(this).apply {
            adapter = ArrayAdapter(this@ProtoActivity, android.R.layout.simple_spinner_dropdown_item, (1..6).map { "Program slot $it" })
        }; layout.addView(slots)
        val destination = Spinner(this).apply {
            adapter = ArrayAdapter(this@ProtoActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Use left address", "Use right address"))
        }; layout.addView(destination)
        programState = TextView(this); layout.addView(programState); showProgramState()
        val sendProgram = Button(this).apply { text = "Send program request"; setOnClickListener {
            start(if (destination.selectedItemPosition == 0) BatterySide.LEFT else BatterySide.RIGHT, slots.selectedItemPosition + 1)
        } }; buttons.add(sendProgram); layout.addView(sendProgram)
        layout.addView(TextView(this).apply { text = "Direct volume experiment\nStandard fitted range 0–15. Uses the address selected above. Current aid volume cannot be queried." })
        val levels = Spinner(this).apply {
            adapter = ArrayAdapter(this@ProtoActivity, android.R.layout.simple_spinner_dropdown_item, (0..15).map { "Volume level $it" })
            setSelection(8)
        }; layout.addView(levels)
        volumeState = TextView(this); layout.addView(volumeState); showVolumeState()
        val sendVolume = Button(this).apply { text = "Send volume request"; setOnClickListener {
            start(if (destination.selectedItemPosition == 0) BatterySide.LEFT else BatterySide.RIGHT, volumeLevel = levels.selectedItemPosition)
        } }; buttons.add(sendVolume); layout.addView(sendVolume)
        fun addControl(key: String, label: String, range: IntRange, initial: Int, send: (Int) -> Unit) {
            layout.addView(TextView(this).apply { text = "$label experiment\nUses the selected address. Last request is remembered; aid state cannot be queried." })
            val choices = Spinner(this).apply {
                adapter = ArrayAdapter(this@ProtoActivity, android.R.layout.simple_spinner_dropdown_item, range.map { "$label $it" })
                setSelection(initial-range.first)
            }; layout.addView(choices)
            val result = TextView(this); extraStates[key] = result; layout.addView(result)
            showExtraState(key)
            val button = Button(this).apply { text = "Send $key request"; setOnClickListener { send(range.first + choices.selectedItemPosition) } }
            buttons.add(button); layout.addView(button)
        }
        addControl("balance", "Sound Sharpness", -8..7, 0) {
            start(if (destination.selectedItemPosition == 0) BatterySide.LEFT else BatterySide.RIGHT, balanceLevel = it)
        }
        addControl("tinnitus", "Tinnitus level", 0..15, 0) {
            start(if (destination.selectedItemPosition == 0) BatterySide.LEFT else BatterySide.RIGHT, tinnitusLevel = it)
        }
        layout.addView(Button(this).apply { text = "Stop"; setOnClickListener { cancelled.set(true) } })
        status = TextView(this).apply { text = "Ready. Startup profile shown; addresses are required." }; layout.addView(status)
        val scroll = ScrollView(this).apply { addView(layout, ViewGroup.LayoutParams(-1,-2)) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        setContentView(scroll)
        layout.requestFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    private fun preflight(): AudioDeviceInfo {
        check((getSystemService(POWER_SERVICE) as PowerManager).isInteractive && !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) { "Turn on and unlock the phone." }
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        check(notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) { "Turn off Do Not Disturb, then retry." }
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        check(audio.mode == AudioManager.MODE_NORMAL) { "Call or communication audio is active." }
        check(audio.ringerMode != AudioManager.RINGER_MODE_SILENT) { "Unmute the phone, then retry." }
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        check(maximum > 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC) >= (maximum+1)/2) { "Raise media volume to at least 50%, then retry." }
        val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val external = setOf(3,4,7,8,11,12,22,23,26,27)
        check(devices.none { it.type in external }) { "Disconnect external audio outputs before speaker playback." }
        return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: error("Built-in speaker unavailable.")
    }
    private fun showProgramState() {
        val slot = getPreferences(MODE_PRIVATE).getInt("lastProgramRequest", 0)
        programState.text = if (slot in 1..6) "Last completed request: slot $slot. Current aid program unknown." else "No completed program request. Current aid program unknown."
    }
    private fun showVolumeState() {
        val level = getPreferences(MODE_PRIVATE).getInt("lastVolumeRequest", -1)
        volumeState.text = if (level in 0..15) "Last completed volume request: $level. Current aid volume unknown." else "No completed volume request. Current aid volume unknown."
    }
    private fun showExtraState(key: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        extraStates[key]?.text = if (prefs.contains("last_$key")) "Last completed $key request: ${prefs.getInt("last_$key",0)}. Current aid state unknown." else "No completed $key request."
    }
    private fun start(side: BatterySide, programSlot: Int? = null, volumeLevel: Int? = null, balanceLevel: Int? = null, tinnitusLevel: Int? = null) {
        if (busy.get()) return
        try {
            val values = fields.map { it.text.toString().trim() }
            val address = Integer.decode(values[if (side == BatterySide.LEFT) 0 else 1])
            require(address in 0..255) { "Address must be a qualified byte, 0–255." }
            val profile = AcousticProfile(values[2].toInt(),values[3].toInt(),values[4].toInt(),values[5].toDouble(),values[6].toDouble(),xor.isChecked)
            val speaker = preflight()
            val pcm = when {
                programSlot != null -> ProtoRequest.programPcm(programSlot,address,profile)
                volumeLevel != null -> ProtoRequest.volumePcm(volumeLevel,address,profile)
                balanceLevel != null -> ProtoRequest.balancePcm(balanceLevel,address,profile)
                tinnitusLevel != null -> ProtoRequest.tinnitusPcm(tinnitusLevel,address,profile)
                else -> ProtoRequest.pcm(side,address,profile)
            }
            val request = when {
                programSlot != null -> "program slot $programSlot"
                volumeLevel != null -> "volume $volumeLevel"
                balanceLevel != null -> "Sound Sharpness $balanceLevel"
                tinnitusLevel != null -> "tinnitus $tinnitusLevel"
                else -> "${side.name.lowercase()} battery"
            }
            if (!busy.compareAndSet(false,true)) return
            cancelled.set(false); buttons.forEach { it.isEnabled = false }; status.text = "Sending $request using ${side.name.lowercase()} address…"
            Thread({
                var track: AudioTrack? = null
                var message = "$request request played."
                var completed = false
                try {
                    preflight()
                    track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(profile.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(pcm.size).setTransferMode(AudioTrack.MODE_STREAM).build()
                    // Match the inspected Signia streaming player; validate before/after loading PCM.
                    check(track.state != AudioTrack.STATE_UNINITIALIZED) { "AudioTrack creation failed." }
                    check(track.setPreferredDevice(speaker)) { "Speaker preference rejected." }
                    check(track.write(pcm,0,pcm.size) == pcm.size) { "Incomplete audio buffer." }
                    check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack initialization failed after loading PCM." }
                    if (!cancelled.get()) {
                        track.play()
                        val started = android.os.SystemClock.elapsedRealtime()
                        val deadline = started + pcm.size.toLong()*1000/(profile.sampleRate*2)+2000
                        while (track.playbackHeadPosition < pcm.size/2 && !cancelled.get()) {
                            check(android.os.SystemClock.elapsedRealtime() < deadline) { "Playback timed out." }
                            val route = track.routedDevice
                            check(route?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || (route == null && android.os.SystemClock.elapsedRealtime()-started < 200)) { "Built-in speaker route could not be confirmed." }
                            Thread.sleep(10)
                        }
                        completed = !cancelled.get()
                    }
                } catch (failure: Exception) { message = "Playback failed: ${failure.message}" }
                finally {
                    track?.let { runCatching { it.stop() }; runCatching { it.release() } }
                    if (cancelled.get()) message = "Stopped; the aid may have received part or all of the request."
                    busy.set(false)
                    runOnUiThread {
                        if (completed && programSlot != null) {
                            getPreferences(MODE_PRIVATE).edit().putInt("lastProgramRequest",programSlot).apply()
                            showProgramState()
                        }
                        if (completed && volumeLevel != null) {
                            getPreferences(MODE_PRIVATE).edit().putInt("lastVolumeRequest",volumeLevel).apply()
                            showVolumeState()
                        }
                        if (completed) {
                            val key = if (balanceLevel != null) "balance" else if (tinnitusLevel != null) "tinnitus" else null
                            if (key != null) {
                                getPreferences(MODE_PRIVATE).edit().putInt("last_$key",balanceLevel ?: tinnitusLevel!!).apply()
                                showExtraState(key)
                            }
                        }
                        status.text = message; buttons.forEach { it.isEnabled = true }
                    }
                }
            },"acoustic-playback").start()
        } catch (failure: Exception) { status.text = "Check settings: ${failure.message ?: "Enter the configured address and profile."}" }
    }
    override fun onPause() { cancelled.set(true); super.onPause() }
}
