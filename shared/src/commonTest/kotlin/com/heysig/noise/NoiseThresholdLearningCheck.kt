package com.heysig.noise

fun main() {
    val settings=NoiseSettings(programs=listOf(1,2,null,3,4,5))
    val context=NoiseLearningContext(settings,"microphone A","fitting A")
    fun populated(): NoiseThresholdLearner = NoiseThresholdLearner(context).also { learner ->
        repeat(24) { i -> learner.add(if(i%2==0) -72.0 else -54.0,i*15_000L) }
    }
    val learner=populated()
    val now=360_000L
    val proposal=checkNotNull(learner.suggest(now))
    check(settings.boundaries==listOf(-60.0,-50.0,-40.0,-30.0,-20.0)) // observation never mutates settings
    check(proposal.boundaries==listOf(-62.0,-50.0,-40.0,-30.0,-20.0)) // partial coverage and bounded shift
    check(proposal.counts==listOf(12,12,0,0,0,0))
    check(runCatching { proposal.copy(boundaries=listOf(-65.0,-50.0,-40.0,-30.0,-20.0)).approved(context,learner.revision,now) }.isFailure)
    check(runCatching { proposal.copy(counts=List(6) { 0 }).approved(context,learner.revision,now) }.isFailure)
    val approved=proposal.approved(context,learner.revision,now)
    check(approved.programs==settings.programs && approved.confirmations==settings.confirmations)
    check(approved.cooldownSeconds==settings.cooldownSeconds && approved.sleepProgram==settings.sleepProgram)
    check(approved.boundaries==proposal.boundaries)
    for(changed in listOf(context.copy(source="microphone B"),context.copy(fitting="fitting B"),
        context.copy(settings=settings.copy(captureSeconds=4)),context.copy(settings=settings.copy(margin=1.0)),
        context.copy(settings=settings.copy(programs=listOf(2,2,null,3,4,5))))) {
        check(runCatching { proposal.approved(changed,learner.revision,now) }.isFailure)
    }
    check(runCatching { proposal.approved(context,learner.revision,now+60_000) }.isFailure)
    check(runCatching { proposal.approved(context,learner.revision,now-1) }.isFailure)
    learner.add(-72.0,375_000)
    check(runCatching { proposal.approved(context,learner.revision,375_000) }.isFailure)
    val invalid=NoiseThresholdLearner(context)
    for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,-121.0,1.0)) invalid.add(value,0)
    invalid.add(-72.0,-1)
    check(invalid.sampleCount==0 && invalid.suggest(now)==null)
    invalid.add(-72.0,1000);invalid.add(-54.0,1000);invalid.add(-54.0,999)
    check(invalid.sampleCount==1)
    val single=NoiseThresholdLearner(context)
    repeat(40) { single.add(-72.0,it*15_000L) }
    check(single.suggest(600_000)==null) // one noise band cannot redefine every level
    val short=NoiseThresholdLearner(context)
    repeat(24) { short.add(if(it%2==0) -72.0 else -54.0,it*1000L) }
    check(short.suggest(now)==null)
    val overlap=NoiseThresholdLearner(context)
    repeat(24) { overlap.add(if(it%2==0) -60.1 else -59.9,it*15_000L) }
    check(overlap.suggest(now)==null)
    val narrow=NoiseThresholdLearner(context.copy(settings=settings.copy(boundaries=listOf(-60.0,-55.0,-40.0,-30.0,-20.0))))
    repeat(24) { narrow.add(if(it%2==0) -61.0 else -55.1,it*15_000L) }
    check(narrow.suggest(now)==null) // do not break the margin between neighboring boundaries
    val unchanged=NoiseThresholdLearner(context)
    repeat(24) { unchanged.add(if(it%2==0) -66.0 else -54.0,it*15_000L) }
    check(unchanged.suggest(now)==null)
    val old=populated()
    check(old.suggest(now+NoiseThresholdLearner.MAX_AGE)==null)
    old.add(-72.0,now+NoiseThresholdLearner.MAX_AGE)
    check(old.sampleCount==1 && old.suggest(now+NoiseThresholdLearner.MAX_AGE)==null)
    val bounded=NoiseThresholdLearner(context)
    repeat(300) { bounded.add(-72.0,it*1000L) }
    check(bounded.sampleCount==NoiseThresholdLearner.MAX_SAMPLES)
    val fractionalContext=context.copy(settings=settings.copy(boundaries=listOf(-60.06,-50.0,-40.0,-30.0,-20.0)))
    val fractional=NoiseThresholdLearner(fractionalContext)
    repeat(24) { fractional.add(if(it%2==0) -72.0 else -54.0,it*15_000L) }
    val fractionalProposal=checkNotNull(fractional.suggest(now))
    check(kotlin.math.abs(fractionalProposal.boundaries[0]+60.06)<=2.0)
    fractionalProposal.approved(fractionalContext,fractional.revision,now)
    val outlier=populated()
    outlier.add(-120.0,375_000)
    check(outlier.suggest(390_000)?.boundaries==proposal.boundaries)
    println("PASS: bounded scalar learning, partial coverage, outliers, insufficient/overlapping evidence, explicit context/revision/expiry-bound approval and unchanged assignments")
}
