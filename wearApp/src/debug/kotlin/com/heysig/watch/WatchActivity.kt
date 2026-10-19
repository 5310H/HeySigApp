package com.heysig.watch

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*
import com.heysig.control.VoicePreference
import com.heysig.companion.PrivacyNotice

/** Experimental standalone Wi-Fi client; never emits hearing-aid command tones. */
class WatchActivity : Activity() {
    private lateinit var status: TextView
    private val working = AtomicBoolean(false)
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var speechIssue = "Speech engine is starting. Try again shortly."
    private var conversation = UUID.randomUUID().toString()
    private var lastRequest: String? = null
    private var lastText: String? = null
    private var capturedAt = 0L
    @Volatile private var active = false
    @Volatile private var connection: HttpsURLConnection? = null
    @Volatile private var generation = 0
    private var deferred: (() -> Unit)? = null
    private val voicePrefs by lazy { getPreferences(MODE_PRIVATE) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        lastRequest = getPreferences(MODE_PRIVATE).getString("lastRequest",null)
        lastStatus = if(File(filesDir,"pairing.json").exists()) "Ready" else "Setup needed"
        speech = TextToSpeech(this) { result ->
            runOnUiThread {
                val language = if(result == TextToSpeech.SUCCESS) speech?.setLanguage(Locale.US) else null
                speechReady = language != null && language >= TextToSpeech.LANG_AVAILABLE
                speechIssue = if(result != TextToSpeech.SUCCESS) "Speech engine unavailable. Check watch text-to-speech settings."
                    else "English speech data unavailable. Check watch text-to-speech language settings."
                if(speechReady) speech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {}
                    @Deprecated("Platform speech callback")
                    override fun onError(id: String?) { runOnUiThread { if(active) showSpeechIssue("Speech output failed. Check watch speech data and media volume.") } }
                })
            }
        }
        render()
    }
    private val shake by lazy { ShakeTrigger(this) {
        if(active) { ShakeTrigger.acknowledge(this); primaryShake() }
    } }
    private fun primaryShake() { findViewById<android.view.View>(android.R.id.content)?.let { primaryForShake?.performClick() } }
    private var primaryForShake: Button?=null
    private fun shakeOptions() {
        val choices=getSharedPreferences("heysig-gesture",MODE_PRIVATE)
        android.app.AlertDialog.Builder(this).setTitle("Shake to speak")
            .setMessage("Three deliberate shakes start Speak while HeySig is open. A short vibration acknowledges the gesture. For screen-off use, choose Sleeping-watch shake in More. Movement may trigger speech accidentally.")
            .setPositiveButton(if(choices.getBoolean("shake",false)) "Turn Off" else "Turn On") { _,_ ->
                val enabled=!choices.getBoolean("shake",false)
                if(choices.edit().putBoolean("shake",enabled).commit()) { shake.stop();if(enabled && active) shake.start() }
            }.setNegativeButton("Cancel",null).show()
    }
    private var lastStatus = "Ready"
    private fun render() {
        val home = WatchStyle.home(this,WatchStyle.Labels(R.string.speak,R.string.stop,R.string.more,R.string.speech_off,R.string.connect,R.string.speak_command),lastStatus,
            !voicePrefs.getBoolean("voicePrompts",true) && !voicePrefs.getBoolean("spokenResponses",true),
            speakAction@ {
            if(working.get()) { reply("Wait for the current result or press Stop"); return@speakAction }
            speechConsent()

            },{
                SleepingShakeService.stop(this)
                generation++; connection?.disconnect(); working.set(false)
                send("stop",listOf("stop"))
            },{ more() })
        primaryForShake=home.primary; status=home.status; setContentView(home.view)
    }
    private var wifiCheck: WatchWifiCheck?=null
    private fun checkWifi() { wifiCheck?.close();wifiCheck=WatchWifiCheck(this) { send("status",listOf("status")) }.show() }
    private fun more() {
        android.app.AlertDialog.Builder(this).setTitle("More")
            .setItems(arrayOf("Read status","Check last result","Speech options","Connection & full status","Hands-free","Shake to speak","Check Wi-Fi","Sleeping-watch shake","Type command")) { _,index ->
                when(index) {
                    8 -> {
                        val input = EditText(this).apply { hint="Command"; setSingleLine() }
                        android.app.AlertDialog.Builder(this).setTitle("Type command").setView(input)
                            .setNegativeButton("Cancel",null).setPositiveButton("Send") { _,_ ->
                                val text=input.text.toString()
                                if(text.isBlank() || text.length>512) reply("Use a command of 1–512 characters")
                                else send(text,listOf(text))
                            }.show()
                    }
                    7 -> SleepingShakeOptions.show(this)
                    6 -> checkWifi()
                    5 -> shakeOptions()
                    0 -> send("status",listOf("status"))
                    1 -> lastRequest?.let { fetchResult(it) } ?: reply("No previous request")
                    2 -> {
                        val keys=arrayOf("voicePrompts","spokenResponses")
                        android.app.AlertDialog.Builder(this).setTitle("Speech")
                            .setMultiChoiceItems(arrayOf("Voice prompts","Spoken responses"),keys.map { voicePrefs.getBoolean(it,true) }.toBooleanArray()) { dialog,item,value ->
                                if(!voicePrefs.edit().putBoolean(keys[item],value).commit()) {
                                    (dialog as android.app.AlertDialog).listView.setItemChecked(item,!value); reply("Could not save speech choice")
                                }
                                if(!value) speech?.stop()
                            }.setNeutralButton("Test reply") { _,_ ->
                                reply(if(voicePrefs.getBoolean("spokenResponses",true)) "This is a HeySig spoken reply test." else "Spoken responses are off. Turn them on to test speech.")
                            }
                            .setPositiveButton("Done",null).setOnDismissListener { render() }.show()
                    }
                    4 -> {
                        val commands=arrayOf("hands free on","pause listening for 30 minutes","hands free off","hands free status","end hands free session")
                        android.app.AlertDialog.Builder(this).setTitle("Phone hands-free")
                            .setItems(arrayOf("On","Pause 30 minutes","Off","Status","End session")) { _,item -> send(commands[item],listOf(commands[item])) }
                            .setNegativeButton("Close",null).show()
                    }
                    3 -> android.app.AlertDialog.Builder(this).setTitle("Development connection")
                        .setMessage((if(File(filesDir,"pairing.json").exists()) "Chromebook relay configured." else "Setup needed from Chromebook.")+"\n"+lastStatus)
                        .setPositiveButton("Close",null).show()
                }
            }.setNegativeButton("Close",null).show()
    }
    private fun speechConsent() {
        if(!voicePrefs.getBoolean("speechDisclosureV1",false)) {
            android.app.AlertDialog.Builder(this).setTitle("Optional speech recognition")
                .setMessage(PrivacyNotice.SPEECH.replace("connected phone", "Chromebook through the development Wi-Fi relay") + " The relay retains command digests and final replies, which can contain settings or recognized words.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(voicePrefs.edit().putBoolean("speechDisclosureV1",true).commit()) launchSpeech()
                    else reply("Could not save speech choice")
                }.show()
        } else launchSpeech()
    }
    private fun launchSpeech() {
        speech?.stop()
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
                if(voicePrefs.getBoolean("voicePrompts",true)) putExtra(RecognizerIntent.EXTRA_PROMPT,"HeySig command")
            },41)
        } catch(e:Exception) { reply("Watch speech service unavailable. Use More → Type command") }
    }
    override fun onResume() { super.onResume(); active = true; SleepingShakeService.activityVisible = true
        if(getSharedPreferences("heysig-gesture",MODE_PRIVATE).getBoolean("shake",false)) shake.start() }
    override fun onPostResume() { super.onPostResume(); consumeShakeTap(); deferred?.let { deferred=null; it() } }
    override fun onPause() {
        wifiCheck?.close();wifiCheck=null;
        shake.stop()
        SleepingShakeService.activityVisible = false
        active = false; generation++; connection?.disconnect(); working.set(false); speech?.stop()
        super.onPause()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    private fun consumeShakeTap() {
        if(SleepingShakeService.consume(intent)) {
            if(getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) reply("Unlock the watch, then tap Speak")
            else primaryShake()
        }
    }
    override fun onDestroy() { generation++; connection?.disconnect(); speech?.shutdown(); super.onDestroy() }
    @Deprecated("Platform speech result")
    override fun onActivityResult(code:Int,result:Int,data:Intent?) {
        super.onActivityResult(code,result,data)
        if(code == 41 && result == RESULT_OK) {
            val alternatives = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty().take(5)
            if(alternatives.isNotEmpty()) {
                capturedAt = SystemClock.elapsedRealtime()
                deferred = {
                    if(SystemClock.elapsedRealtime()-capturedAt <= 15000) send(alternatives[0],alternatives)
                    else reply("Speech expired. Please speak again")
                }
            }
        }
    }
    private fun reply(text:String, prompt:Boolean = false) {
        lastStatus=text; status.text = text
        if(active && voicePrefs.getBoolean(if(prompt) "voicePrompts" else "spokenResponses",true)) {
            if(!speechReady) showSpeechIssue(speechIssue)
            else if(speech?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"heysig-watch-result") != TextToSpeech.SUCCESS)
                showSpeechIssue("Speech output could not start. Check watch text-to-speech settings.")
        }
    }
    private fun showSpeechIssue(issue: String) {
        lastStatus = "$lastStatus\n$issue"; status.text = lastStatus
        android.app.AlertDialog.Builder(this).setTitle("Spoken reply unavailable").setMessage(issue)
            .setPositiveButton("Close",null).show()
    }
    private fun withWifi(action:(Network)->JSONObject): JSONObject {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val available = CountDownLatch(1)
        var network: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(value:Network) { network = value; available.countDown() }
            override fun onUnavailable() { available.countDown() }
        }
        manager.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback,10000)
        try { check(available.await(11,TimeUnit.SECONDS)); return action(checkNotNull(network) { "Connect watch to Wi-Fi" }) }
        finally { manager.unregisterNetworkCallback(callback) }
    }
    private fun request(network: Network, path:String, body: JSONObject? = null): JSONObject {
        val config = JSONObject(File(filesDir,"pairing.json").readText())
        val host = config.getString("host")
        require(host.matches(Regex("[a-zA-Z0-9.-]+")))
        val fingerprint = config.getString("fingerprint")
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        val manager = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String) { error("Client authentication unsupported") }
            override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String) {
                check(chain.isNotEmpty())
                chain[0].checkValidity()
                val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded).joinToString("") { "%02x".format(it.toInt() and 255) }
                check(MessageDigest.isEqual(actual.toByteArray(),fingerprint.toByteArray())) { "Relay certificate changed" }
            }
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null,arrayOf(manager),SecureRandom()) }
        val url = java.net.URL("https://$host:${config.getInt("port")}$path")
        val http = network.openConnection(url) as HttpsURLConnection
        connection = http
        http.sslSocketFactory = tls.socketFactory
        // The exact pinned certificate is the identity, rather than a public DNS name.
        http.hostnameVerifier = HostnameVerifier { _,_ -> true }
        http.connectTimeout = 5000; http.readTimeout = 30000; http.instanceFollowRedirects = false
        http.setRequestProperty("Authorization","Bearer ${config.getString("token")}")
        try {
            if(body != null) {
                http.requestMethod = "POST"; http.doOutput = true
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                http.setFixedLengthStreamingMode(bytes.size)
                http.setRequestProperty("Content-Type","application/json")
                http.outputStream.use { it.write(bytes) }
            }
            check(http.responseCode == 200) { "Relay rejected the request (${http.responseCode})" }
            val raw = http.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while(true) {
                    val count = input.read(buffer); if(count == -1) break
                    check(output.size()+count <= 8192); output.write(buffer,0,count)
                }
                output.toString("UTF-8")
            }
            return JSONObject(raw)
        } finally { http.disconnect(); if(connection === http) connection = null }
    }
    private fun send(text:String, alternatives:List<String>) {
        if(WatchWifiCheck.requested(text)) { checkWifi();return }
        val preference = VoicePreference.parse(text)
        if(preference != null) {
            if(alternatives.any { VoicePreference.parse(it) != preference }) { reply("Speech alternatives disagree. Say voice prompts or voice responses, then on or off"); return }
            if(!voicePrefs.edit().putBoolean(preference.key,preference.enabled).commit()) { reply("Could not save voice setting"); return }
            speech?.stop()
            render()
            reply(preference.description,preference.prompts)
            return
        }
        if(!active || !working.compareAndSet(false,true)) return
        val myGeneration = ++generation
        val id = UUID.randomUUID().toString()
        // Stop must preserve lookup of the interrupted command, including an uncertain result.
        val stopping = text.trim().lowercase(Locale.ROOT) in listOf("stop","cancel")
        if(!stopping) {
            if(!voicePrefs.edit().putString("lastRequest",id).commit()) {
                working.set(false); reply("Could not remember request; nothing sent"); return
            }
            lastRequest = id
        }
        lastText = text
        status.text = "Heard: $text\nSending…"
        Thread({
            val answer = try {
                withWifi { network ->
                    check(active && generation == myGeneration) { "Cancelled before connection" }
                    val session = request(network,"/session")
                    check(active && generation == myGeneration) { "Cancelled before submission" }
                    request(network,"/command",JSONObject().put("id",id).put("conversation",conversation).put("text",text)
                        .put("alternatives",JSONArray(alternatives)).put("nonce",session.getString("nonce")))
                }
            } catch(e:Exception) { JSONObject().put("text","No confirmed result. Check Wi-Fi and use Check last result. No automatic retry.") }
            runOnUiThread { if(generation == myGeneration) { working.set(false); if(active) reply(answer.getString("text"),answer.optBoolean("prompt",answer.optString("phase") == "CLARIFY")) } }
        },"heysig-watch-command").start()
    }
    private fun fetchResult(id:String) {
        if(!active || !working.compareAndSet(false,true)) return
        val myGeneration = ++generation
        Thread({
            val answer = try { withWifi { network -> request(network,"/result/$id") } } catch(e:Exception) { JSONObject().put("text","Could not retrieve the last result") }
            runOnUiThread { if(generation == myGeneration) { working.set(false); if(active) reply(answer.getString("text"),answer.optBoolean("prompt",answer.optString("phase") == "CLARIFY")) } }
        },"heysig-watch-result").start()
    }
}
