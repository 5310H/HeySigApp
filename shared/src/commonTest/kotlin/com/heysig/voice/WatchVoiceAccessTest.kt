package com.heysig.voice

import org.junit.Assert.*
import org.junit.Test

class WatchVoiceAccessTest {
    @Test fun activeAuthoritySurvivesLockWithoutUnlockingDevice() {
        val unlocked=WatchVoiceAccess(true,true,true,false)
        val locked=unlocked.copy(deviceLocked=true)
        assertTrue(unlocked.mayCapture);assertTrue(locked.mayCapture)
        assertTrue(unlocked.mayStartSession);assertFalse(locked.mayStartSession)
        assertTrue(locked.deviceLocked)
    }
    @Test fun lockDoesNotCreateAuthorityOrOverrideRevocation() {
        assertFalse(WatchVoiceAccess(false,true,true,true).mayCapture)
        assertFalse(WatchVoiceAccess(true,false,true,true).mayCapture)
        assertFalse(WatchVoiceAccess(true,true,false,true).mayCapture)
        assertFalse(WatchVoiceAccess(false,false,false,false).mayStartSession)
    }
}
