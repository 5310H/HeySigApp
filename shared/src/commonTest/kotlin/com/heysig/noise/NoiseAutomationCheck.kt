package com.heysig.noise

fun main() {
    check(isFreshNoiseSample(1000,31000,30))
    check(!isFreshNoiseSample(1000,31001,30))
    check(!isFreshNoiseSample(1000,999,30))
    check(!isFreshNoiseSample(-1,1000,30))
    val calibration = NoiseCalibration(List(6) { i -> listOf(-85.0+i*12,-84.0+i*12,-83.0+i*12) })
    val boundaries = calibration.thresholds(2.0)
    check(boundaries == listOf(-78.0,-66.0,-54.0,-42.0,-30.0))
    check(runCatching { NoiseCalibration().thresholds(2.0) }.isFailure)
    check(runCatching { NoiseCalibration(List(6) { listOf(-30.0,-29.0,-28.0) }).thresholds(2.0) }.isFailure)
    check(runCatching { NoiseCalibration(List(6) { listOf(Double.NaN) }) }.isFailure)
    fun measurement(value:Double, flags:Set<MeasurementFlag> = emptySet()) =
        NoiseMeasurement(16000,80000,5,value,value,value,value,0.0,0.1,flags)
    var captured = NoiseCalibration()
    repeat(6) { i -> calibration.levels[i].forEach { value -> captured = captured.add(i+1,measurement(value)) } }
    check(captured == calibration)
    check(runCatching { captured.add(1,measurement(-85.0)) }.isFailure)
    check(runCatching { NoiseCalibration().add(1,measurement(-85.0,setOf(MeasurementFlag.CLIPPING))) }.isFailure)
    check(runCatching { NoiseCalibration().add(0,measurement(-85.0)) }.isFailure)
    val settings = NoiseSettings(programs=listOf(1,1,null,3,4,5),boundaries=boundaries)
    settings.validate((1..6).toSet())
    val profile = NoiseCalibrationProfile(calibration,"source-A",5,2.0)
    check(profile.matches(settings,"source-A"))
    check(!profile.matches(settings,"source-B"))
    check(!profile.matches(settings.copy(captureSeconds=4),"source-A"))
    check(!profile.matches(settings.copy(margin=1.0),"source-A"))
    check(!profile.matches(settings.copy(boundaries=listOf(-79.0,-66.0,-54.0,-42.0,-30.0)),"source-A"))
    val safe = NoiseSafety(true,true,true,true,true)
    check(automaticNoise(NoiseAutomaticState(),-10.0,1000,settings,safe,null).target == null)
    check(!NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED)
    listOf(safe.copy(calibrated=false),safe.copy(physicallyAccepted=false),safe.copy(foreground=false),
        safe.copy(unlocked=false),safe.copy(audioFree=false),safe.copy(manualOverride=true),safe.copy(sleeping=true)).forEach {
        check(automaticNoise(NoiseAutomaticState(),-10.0,1000,settings,it,1).target == null)
    }
    check(automaticNoise(NoiseAutomaticState(),-10.0,1000,settings.copy(sleepProgram=1),safe,1).target == null)
    var asleepTarget = NoiseAutomaticState()
    repeat(4) { asleepTarget = automaticNoise(asleepTarget,-10.0,(it+1)*1000L,settings.copy(sleepProgram=5),safe,1).also { check(it.target == null) }.state }
    var state = NoiseAutomaticState()
    for(t in 1L..2) { val d=automaticNoise(state,-10.0,t*1000,settings,safe,1); check(d.target == null); state=d.state }
    val request = automaticNoise(state,-10.0,3000,settings,safe,1)
    check(request.target == 5 && request.state.pendingTarget == 5)
    check(automaticNoise(request.state,-10.0,4000,settings,safe,1).target == null)
    check(completeNoise(request.state,999,true,5000) == request.state)
    val completed = completeNoise(request.state,request.state.token,true,5000)
    check(completed.lastSuccessMillis == 5000L && !completed.halted)
    check(automaticNoise(completed,-90.0,64999,settings,safe,5).state.observation.count == 0)
    check(automaticNoise(completed,-90.0,4999,settings,safe,5).target == null)
    val after = automaticNoise(completed,-90.0,65000,settings,safe,5)
    check(after.target == null && after.state.observation.count == 1)
    val failed = completeNoise(request.state,request.state.token,false,5000)
    check(failed.halted && automaticNoise(failed,-10.0,99000,settings,safe,1).target == null)
    check(automaticNoise(state,null,3000,settings,safe,1).state.observation.count == 0)
    check(automaticNoise(state,-10.0,99000,settings,safe,1).state.observation.count == 1)
    var same = NoiseAutomaticState()
    repeat(4) { same=automaticNoise(same,-10.0,(it+1)*1000L,settings,safe,5).also { check(it.target == null) }.state }
    check(automaticNoise(state,-60.0,3000,settings,safe,1).state.observation.count == 0) // unused
    val suppressed = automaticNoise(state,-10.0,3000,settings,safe.copy(manualOverride=true),1)
    check(suppressed.state.observation.count == 0)
    println("PASS: scalar six-level calibration, rejected overlap, readiness gates, confirmation, cooldown, no-op, failure and stale completion")
}
