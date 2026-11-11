package com.heysig.app

import android.app.Instrumentation
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableStatusCodes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fake SDK tasks exercise actual completion order without Data Layer, audio or wearer settings. */
internal object WatchCapabilityAcceptance {
    fun verify(test:Instrumentation):String {
        val queue=WatchAutomaticCapabilityQueue()
        val pending=TaskCompletionSource<Void>();val entered=CountDownLatch(1)
        val calls=mutableListOf<String>();lateinit var renewed:Task<Unit>
        test.runOnMainSync {
            queue.update(true,{ calls.add("add");entered.countDown();pending.task })
            queue.update(false,{ calls.add("remove");Tasks.forResult<Void>(null) })
            renewed=queue.update(true,{ calls.add("renew");Tasks.forException<Void>(ApiException(Status(WearableStatusCodes.DUPLICATE_CAPABILITY))) })
        }
        check(entered.await(10,TimeUnit.SECONDS))
        test.runOnMainSync { check(calls==listOf("add")) { "Cleanup raced an unfinished registration" } }
        pending.setResult(null);Tasks.await(renewed,10,TimeUnit.SECONDS)
        test.runOnMainSync { check(calls==listOf("add","remove","renew")) }
        lateinit var failed:Task<Unit>;lateinit var cleaned:Task<Unit>;lateinit var cancelled:Task<Unit>
        test.runOnMainSync {
            failed=queue.update(true,{ Tasks.forException<Void>(IllegalStateException("injected registration failure")) })
            cleaned=queue.update(false,{ Tasks.forException<Void>(ApiException(Status(WearableStatusCodes.UNKNOWN_CAPABILITY))) })
            cancelled=queue.update(true,{ error("Cancelled session must not advertise") },{ false })
        }
        Tasks.await(cleaned,10,TimeUnit.SECONDS);Tasks.await(cancelled,10,TimeUnit.SECONDS)
        check(failed.isComplete && !failed.isSuccessful)
        return "PASS: serialized add/remove/restart, stale duplicate and missing cleanup, real failures retained, cancelled start skipped; fake SDK tasks only"
    }
}
