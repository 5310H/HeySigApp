package com.heysig.control

fun phraseImportCheck() {
    val fit=Fitting(12,12,12,listOf(Program(1,"Universal",TinnitusCapability.SUPPORTED)))
    var allow=true
    var writes=0
    val phrases=PersonalPhrases { if(allow) { writes++;true } else false }
    fun manage(text: String, origin: String="phone", key: String="fit", now: Long=10) = phrases.manage(text,fit,key,origin,now)
    check(manage("when I say movie time I mean program Universal")!!.contains("Remember"))
    check(writes==0)
    check(manage("remember that correction")!!.contains("Remembered"));check(writes==1)
    val entries=listOf("quiet time" to "volume three", "bright sound" to "balance seven")
    check(phrases.stageImport(entries,fit,"fit","phone",10).contains("quiet time means volume three"))
    check(phrases.awaitingConfirmation)
    check(manage("confirm phrase import","watch")!!.contains("device"));check(writes==1)
    check(manage("confirm phrase import")!!.contains("Imported 2"));check(writes==2)
    check(phrases.resolve("quiet time",fit,"fit")==AppIntent.Control(ManualRequest.SetLevel(LevelControl.VOLUME,3)))
    val saved=phrases.records
    for(invalid in listOf(listOf("stop" to "volume ten"),listOf("fresh phrase" to "volume up"),listOf("fresh phrase" to "volume three","fresh phrase" to "balance zero"),listOf("fresh phrase" to "volume three","bad phrase" to "destroy everything"))) {
        check(runCatching { phrases.stageImport(invalid,fit,"fit","phone",10) }.isFailure)
        check(phrases.records==saved && !phrases.awaitingConfirmation)
    }
    phrases.stageImport(listOf("fresh phrase" to "volume ten"),fit,"fit","phone",10)
    check(manage("confirm phrase import",now=400_000)!!.contains("expired"));check(writes==2)
    phrases.stageImport(listOf("fresh phrase" to "volume ten"),fit,"fit","phone",10)
    check(manage("confirm phrase import",key="changed")!!.contains("changed"));check(writes==2)
    phrases.stageImport(listOf("fresh phrase" to "volume ten"),fit,"fit","phone",10)
    allow=false
    check(manage("confirm phrase import")!!.contains("Could not save"));check(phrases.records==saved)
    allow=true
    phrases.stageImport(listOf("fresh phrase" to "volume ten"),fit,"fit","phone",10)
    check(manage("volume five")!!.contains("cancelled"));check(writes==2)
    check(runCatching { phrases.stageImport(listOf("configure hearing aids" to "volume ten"),fit,"fit","phone",10) { FittingWorkflow.accepts(it) } }.isFailure)
    println("PASS: conversational corrections and reviewed phrase import atomicity, expiry, origin and invalid input")
}
