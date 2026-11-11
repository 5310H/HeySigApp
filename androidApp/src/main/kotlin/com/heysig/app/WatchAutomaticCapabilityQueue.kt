package com.heysig.app

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableStatusCodes

/** Serializes asynchronous discovery changes across explicit sessions, never command authority. */
internal class WatchAutomaticCapabilityQueue {
    private var tail:Task<Unit> = Tasks.forResult(Unit)
    @Synchronized fun update(advertise:Boolean,change:()->Task<Void>,needed:()->Boolean={true}):Task<Unit> {
        val next:Task<Unit> = tail.continueWithTask {
            if(!needed()) Tasks.forResult(Unit)
            else change().continueWith { operation ->
                if(!operation.isSuccessful) {
                    val expected=if(advertise) WearableStatusCodes.DUPLICATE_CAPABILITY else WearableStatusCodes.UNKNOWN_CAPABILITY
                    if((operation.exception as? ApiException)?.statusCode!=expected)
                        throw operation.exception ?: IllegalStateException("Capability change failed")
                }
                Unit
            }
        }
        tail=next
        return next
    }
}
