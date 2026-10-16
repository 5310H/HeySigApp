package com.heysig.desktop

import com.heysig.research.*
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.*
import javax.swing.*

class ProtoWindow : JFrame("HeySig · Proto audio experiment") {
    private val entries = linkedMapOf(
        "Left qualified address" to JTextField(), "Right qualified address" to JTextField(),
        "Sample rate (Hz)" to JTextField("44100"), "Center frequency (Hz)" to JTextField("15360"),
        "Frequency spacing (Hz)" to JTextField("512"), "Bit duration (seconds)" to JTextField("0.0625"),
        "Gain divisor" to JTextField("4"))
    private val xor = JCheckBox("Address-XOR checksum", true)
    private val output = JComboBox(AudioSystem.getMixerInfo().filter { info ->
        AudioSystem.getMixer(info).sourceLineInfo.any { SourceDataLine::class.java.isAssignableFrom(it.lineClass) }
    }.toTypedArray())
    private val status = JTextArea("Ready. Choose basic Signia defaults for the no-pairing test.", 2, 50).apply {
        isEditable = false; lineWrap = true; wrapStyleWord = true; background = UIManager.getColor("Panel.background")
    }
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var line: SourceDataLine? = null
    private val actions = mutableListOf<JButton>()
    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        val form = JPanel(GridLayout(0,2,8,8))
        entries.forEach { (label, field) -> form.add(JLabel(label)); form.add(field) }
        form.add(JLabel("Linux audio output")); form.add(output)
        form.add(xor); form.add(JLabel("Use built-in speakers; check ChromeOS volume and routing."))
        val buttons = JPanel(GridLayout(0,2,8,8))
        buttons.add(JButton("Use basic Signia defaults").apply { addActionListener {
            if (!busy.get()) {
                entries.values.elementAt(0).text = "0"; entries.values.elementAt(1).text = "0"
                val defaults = listOf("44100","15360","512","0.0625","1.2")
                entries.values.drop(2).forEachIndexed { i, field -> field.text = defaults[i] }
                xor.isSelected = true
                status.text = "Basic Signia test preset selected. Press one side and listen for its battery indication."
            }
        } })
        for (side in BatterySide.entries) {
            val play = JButton("Check ${side.name.lowercase()}")
            val export = JButton("Export ${side.name.lowercase()} WAV")
            play.addActionListener { start(side, false) }; export.addActionListener { start(side, true) }
            actions.add(play); actions.add(export); buttons.add(play); buttons.add(export)
        }
        buttons.add(JButton("Stop").apply { addActionListener { stopPlayback() } })
        val panel = JPanel(BorderLayout(8,12)).apply {
            border = BorderFactory.createEmptyBorder(16,16,16,16)
            add(JLabel("Experimental audio · aid response is unverified. No battery percentage is read."), BorderLayout.NORTH)
            add(form,BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply { add(buttons,BorderLayout.NORTH); add(status,BorderLayout.SOUTH) },BorderLayout.SOUTH)
        }
        contentPane = panel
        addWindowListener(object : WindowAdapter() { override fun windowClosing(event: WindowEvent) { stopPlayback() } })
        pack(); setLocationRelativeTo(null)
    }
    private fun stopPlayback() {
        cancelled.set(true)
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
    }
    private fun start(side: BatterySide, export: Boolean) {
        if (busy.get()) return
        try {
            val values = entries.values.map { it.text.trim() }
            val address = Integer.decode(values[if (side == BatterySide.LEFT) 0 else 1])
            require(address in 0..255) { "Qualified address must be 0–255 (decimal or 0x hex)." }
            val profile = AcousticProfile(values[2].toInt(),values[3].toInt(),values[4].toInt(),values[5].toDouble(),values[6].toDouble(),xor.isSelected)
            val pcm = ProtoRequest.pcm(side,address,profile)
            if (export) {
                val chooser = JFileChooser().apply { selectedFile = File("battery-${side.name.lowercase()}.wav") }
                if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
                require(!chooser.selectedFile.exists()) { "Choose a new filename." }
                chooser.selectedFile.writeBytes(ProtoRequest.wav(pcm,profile.sampleRate))
                status.text = "WAV exported. No audio played."
                return
            }
            val mixer = output.selectedItem as? Mixer.Info ?: error("No Linux playback output is available.")
            if (!busy.compareAndSet(false,true)) return
            cancelled.set(false); actions.forEach { it.isEnabled = false }; status.text = "Playing ${side.name.lowercase()} request…"
            Thread({
                var result = "Request played. Listen for the aid's battery indication; reception is unverified."
                try {
                    val format = AudioFormat(profile.sampleRate.toFloat(),16,1,true,false)
                    val track = AudioSystem.getMixer(mixer).getLine(DataLine.Info(SourceDataLine::class.java,format)) as SourceDataLine
                    line = track
                    if (!cancelled.get()) {
                        track.open(format); track.start()
                        var offset = 0
                        while (offset < pcm.size && !cancelled.get()) {
                            val written = track.write(pcm,offset,minOf(2048,pcm.size-offset))
                            check(written > 0) { "Audio output made no progress" }
                            offset += written
                        }
                        if (!cancelled.get()) track.drain()
                    }
                } catch (failure: Exception) { result = "Playback failed: ${failure.message}" }
                finally {
                    line?.let { runCatching { it.close() } }; line = null
                    if (cancelled.get()) result = "Stopped. The aid may have received part or all of the request."
                    busy.set(false)
                    SwingUtilities.invokeLater { status.text = result; actions.forEach { it.isEnabled = true } }
                }
            },"battery-playback").start()
        } catch (failure: Exception) { status.text = "Check settings: ${failure.message ?: "Enter both addresses and numeric profile values."}" }
    }
}
fun main(args: Array<String>) {
    if (args.contains("--check")) {
        val profile = AcousticProfile(44100,15360,512,.0625,4.0,true)
        check(ProtoRequest.pcm(BatterySide.LEFT,0,profile).size == 60632)
        println("PASS: desktop runtime and shared battery generator")
        return
    }
    SwingUtilities.invokeLater {
        val window = ProtoWindow()
        window.isVisible = true
        if (args.contains("--ui-check")) {
            val screenshot = java.awt.image.BufferedImage(window.width,window.height,java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = screenshot.createGraphics(); window.paint(graphics); graphics.dispose()
            javax.imageio.ImageIO.write(screenshot,"png",File("/tmp/heysig-battery-window.png"))
            check(window.width > 0 && window.height > 0)
            println("PASS: desktop window rendered; no audio played")
            window.dispose()
        }
    }
}
