package com.heysig.research

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Experimental offline reconstruction. No playback or device configuration defaults. */
data class AcousticProfile(val sampleRate: Int, val baseFrequency: Int,
    val bandSpace: Int, val bitSeconds: Double, val gainDivisor: Double,
    val addressXor: Boolean) {
    val pulseSamples: Int
    init {
        require(sampleRate in 8000..192000)
        require(bandSpace > 0 && baseFrequency - 1.5 * bandSpace > 0)
        require(baseFrequency + 1.5 * bandSpace < sampleRate / 2.0)
        require(bitSeconds.isFinite() && bitSeconds in 0.001..0.1)
        require(gainDivisor.isFinite() && gainDivisor >= 1.0)
        // .NET Math.Round uses ties-to-even, unlike roundToInt.
        pulseSamples = Math.rint(bitSeconds / 4 * sampleRate).toInt()
        require(pulseSamples >= 100) { "Pulse must accommodate two 50-sample shaping edges" }
    }
}

enum class BatterySide(val command: Int) { LEFT(0x80), RIGHT(0x81) }

object ProtoRequest {
    fun checksum(command: Int, address: Int, addressXor: Boolean): Int {
        require(command in 0..255 && address in 0..255)
        var crc = command
        repeat(8) { crc = ((crc shl 1) xor (if (crc and 128 != 0) 7 else 0)) and 255 }
        return crc xor (if (addressXor) address xor 0xaa else 0)
    }

    fun frequencies(side: BatterySide, address: Int, addressXor: Boolean): IntArray {
        return commandFrequencies(side.command, address, addressXor)
    }

    private fun commandFrequencies(command: Int, address: Int, addressXor: Boolean): IntArray {
        val crc = checksum(command, address, addressXor)
        val a = IntArray(8) { i -> ((if (i % 2 == 0) command else crc) shr (7 - i / 2)) and 1 }
        val b = IntArray(8) { i -> ((if (i % 2 == 0) crc else command) shr (3 - i / 2)) and 1 }
        // F1: A0 at slot 0, A1 at 2; F3 occupies its opposite slot.
        // F2: B0 at slot 1, B1 at 3; F4 occupies its opposite slot.
        return IntArray(32) { i ->
            when (i % 4) {
                0 -> if (a[i / 4] == 0) 0 else 2
                1 -> if (b[i / 4] == 0) 1 else 3
                2 -> if (a[i / 4] == 0) 2 else 0
                else -> if (b[i / 4] == 0) 3 else 1
            }
        }
    }

    fun pcm(side: BatterySide, address: Int, profile: AcousticProfile): ByteArray {
        return commandPcm(side.command, address, profile)
    }

    /** One-based fitted program slot. No readback or platform-specific unmute preamble. */
    fun programPcm(slot: Int, address: Int, profile: AcousticProfile): ByteArray {
        require(slot in 1..6) { "Program slot must be 1–6" }
        return commandPcm(0x40 + slot - 1, address, profile)
    }

    /** Standard 0–15 fitted slider: attenuation position is 15 minus the level. */
    fun volumePcm(level: Int, address: Int, profile: AcousticProfile): ByteArray {
        require(level in 0..15) { "Volume level must be 0–15" }
        return commandPcm(0x1f - level, address, profile)
    }

    /** Signed Sound Sharpness mapping for the captured fitting/platform. */
    fun balancePcm(level: Int, address: Int, profile: AcousticProfile): ByteArray {
        require(level in -8..7) { "Sound Sharpness must be -8–7" }
        return commandPcm(0x20 + (level and 15), address, profile)
    }

    fun tinnitusPcm(level: Int, address: Int, profile: AcousticProfile): ByteArray {
        require(level in 0..15) { "Tinnitus level must be 0–15" }
        return commandPcm(0x3f - level, address, profile)
    }

    private fun commandPcm(command: Int, address: Int, profile: AcousticProfile): ByteArray {
        val n = profile.pulseSamples
        val shape = DoubleArray(n) { j ->
            val edge = when {
                j < 50 -> j
                j >= n - 50 -> n - 1 - j
                else -> -1
            }
            if (edge < 0) 1.0 else 0.5 - 0.5 * cos(PI * edge / 50)
        }
        val waves = Array(4) { f ->
            val hz = profile.baseFrequency + (f - 1.5) * profile.bandSpace
            DoubleArray(n) { j -> sin(j.toDouble() / profile.sampleRate * (2 * PI * hz)) * shape[j] / profile.gainDivisor }
        }
        // GenerateControlTone appends 12 pulse lengths, then shifts by two.
        val samples = DoubleArray(44 * n)
        commandFrequencies(command, address, profile.addressXor).forEachIndexed { i, f ->
            waves[f].copyInto(samples, (i + 2) * n)
        }
        return ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            samples.forEach { putShort((it * 32767).toInt().toShort()) }
        }.array()
    }

    fun wav(pcm: ByteArray, sampleRate: Int): ByteArray {
        require(pcm.size % 2 == 0)
        return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size); put(pcm)
        }.array()
    }
}

/** Positional arguments deliberately require every unresolved configuration value. */
fun main(args: Array<String>) {
    require(args.size == 9) {
        "Usage: left|right|program1..program6|volume0..volume15|balance-8..balance7|tinnitus0..tinnitus15 address on|off sampleRate baseHz bandSpaceHz bitSeconds gainDivisor output.wav"
    }
    val slot = if (args[0].matches(Regex("program[1-6]"))) args[0].last().digitToInt() else null
    val level = if (args[0].matches(Regex("volume(?:[0-9]|1[0-5])"))) args[0].removePrefix("volume").toInt() else null
    val balance = if (args[0].matches(Regex("balance(?:-[1-8]|[0-7])"))) args[0].removePrefix("balance").toInt() else null
    val tinnitus = if (args[0].matches(Regex("tinnitus(?:[0-9]|1[0-5])"))) args[0].removePrefix("tinnitus").toInt() else null
    val side = when(args[0]) { "left" -> BatterySide.LEFT; "right" -> BatterySide.RIGHT; else -> { require(slot != null || level != null || balance != null || tinnitus != null) { "Invalid request" }; null } }
    val address = Integer.decode(args[1])
    val xor = when(args[2]) { "on" -> true; "off" -> false; else -> error("Select on or off") }
    val profile = AcousticProfile(args[3].toInt(), args[4].toInt(), args[5].toInt(), args[6].toDouble(), args[7].toDouble(), xor)
    val output = File(args[8])
    require(!output.exists()) { "Output already exists" }
    val pcm = when {
        slot != null -> ProtoRequest.programPcm(slot,address,profile)
        level != null -> ProtoRequest.volumePcm(level,address,profile)
        balance != null -> ProtoRequest.balancePcm(balance,address,profile)
        tinnitus != null -> ProtoRequest.tinnitusPcm(tinnitus,address,profile)
        else -> ProtoRequest.pcm(requireNotNull(side),address,profile)
    }
    output.writeBytes(ProtoRequest.wav(pcm, profile.sampleRate))
    println("Experimental ${args[0]} WAV written: ${output.path}; not validated for playback")
}
