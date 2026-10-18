package com.heysig.app

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Read-only update verification for hosts where Android disables run-as. No app UI/audio. */
class SettingsFingerprintSmoke : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        val result=Bundle()
        try {
            val directory=File(targetContext.applicationInfo.dataDir,"shared_prefs")
            val hashes=JSONObject()
            for(name in listOf("heysig-core-v1.xml","heysig-personal-phrases-v1.xml","heysig-sleep-v1.xml","heysig-noise-v1.xml")) {
                val file=File(directory,name)
                hashes.put(name,if(file.exists()) MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it.toInt() and 255) } else JSONObject.NULL)
            }
            result.putString("fingerprints",hashes.toString())
            result.putString("result","PASS: read-only settings fingerprints; no UI, edits or playback")
            finish(Activity.RESULT_OK,result)
        } catch(e: Exception) {
            result.putString("result","FAIL: ${e.message}");finish(Activity.RESULT_CANCELED,result)
        }
    }
}
