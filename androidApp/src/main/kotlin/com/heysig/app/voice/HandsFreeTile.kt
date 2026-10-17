package com.heysig.app.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.heysig.app.HomeActivity
import com.heysig.voice.HandsFreeControl

/** A user-selected system tile; it never starts a microphone session silently. */
class HandsFreeTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { label="HeySig listening";state=if(HandsFreeService.running?.isListening==true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE;updateTile() }
    }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION") // Intent overload is the supported API on Android 10–13.
    override fun onClick() {
        val service=HandsFreeService.running
        if(service!=null) { service.control(if(service.isListening) HandsFreeControl.OFF else HandsFreeControl.ON);onStartListening();return }
        val intent=Intent(this,HomeActivity::class.java).setAction("com.heysig.OPEN_HANDSFREE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,1,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else startActivityAndCollapse(intent)
    }
}
