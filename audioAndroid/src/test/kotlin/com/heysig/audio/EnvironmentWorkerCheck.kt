package com.heysig.audio

import com.heysig.noise.EnvironmentClassification
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    fun CountDownLatch.awaitCheck() = check(await(10,TimeUnit.SECONDS)) { "Worker did not finish" }
    val classification=EnvironmentClassification(null,0.0,"Test evidence")
    for(stop in listOf(false,true)) {
        val allowed=AtomicBoolean(true)
        val entered=CountDownLatch(1)
        val resume=CountDownLatch(1)
        val returned=CountDownLatch(1)
        val callback=CountDownLatch(1)
        val callbacks=AtomicInteger()
        val pcm=ShortArray(48000) { 123 }
        val worker=EnvironmentSampleWorker({ allowed.get() },{ _,_ -> pcm },{ _,cancelled ->
            entered.countDown();resume.awaitCheck()
            check(cancelled())
            returned.countDown()
            classification
        },{ 1000 })
        check(worker.sample(3) { result,error,_ ->
            check(result==null && error!=null && pcm.all { it==0.toShort() })
            callbacks.incrementAndGet();callback.countDown()
        })
        entered.awaitCheck()
        check(!worker.sample(3) { _,_,_ -> error("Concurrent sample") })
        if(stop) worker.stop() else allowed.set(false)
        resume.countDown();returned.awaitCheck()
        if(!stop) callback.awaitCheck()
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(worker.busy() && System.nanoTime()<deadline) Thread.sleep(5)
        check(!worker.busy() && pcm.all { it==0.toShort() })
        check(callbacks.get()==if(stop) 0 else 1)
    }
    val complete=CountDownLatch(1)
    val pcm=ShortArray(48000) { 99 }
    lateinit var restart: EnvironmentSampleWorker
    restart=EnvironmentSampleWorker({ true },{ _,_ -> pcm },{ _,_ -> classification },{ 1000 })
    check(restart.sample(3) { result,error,_ ->
        check(result==classification && error==null && pcm.all { it==0.toShort() })
        check(restart.sample(3) { next,failure,_ ->
            check(next==classification && failure==null);complete.countDown()
        }) // Completion must permit an immediate restart, without waiting for thread exit.
    })
    complete.awaitCheck()
    val failure=CountDownLatch(1)
    val broken=EnvironmentSampleWorker({ true },{ _,_ -> throw IllegalStateException("Microphone denied") },
        { _,_ -> error("Must not classify") },{ 1000 })
    check(broken.sample(3) { result,error,_ -> check(result==null && error=="Microphone denied");failure.countDown() })
    failure.awaitCheck();check(!broken.busy())
    println("PASS: inference access loss, cancellation, concurrent rejection, PCM erasure, immediate restart and capture failure")
}
