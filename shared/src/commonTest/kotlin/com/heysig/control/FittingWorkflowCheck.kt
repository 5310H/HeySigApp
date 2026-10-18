package com.heysig.control

fun fittingWorkflowCheck() {
    val fit=Fitting(12,12,12,listOf(Program(1,"Universal",TinnitusCapability.SUPPORTED)))
    var stored: Fitting?=fit
    var writes=0
    var allow=true
    val flow=FittingWorkflow { if(allow) { stored=it;writes++;true } else false }
    fun command(text: String, origin: String="phone", now: Long=10) = flow.handle(text,stored,origin,now).orEmpty()
    check(command("configure hearing aids").contains("draft started"))
    check(command("name program one as Everyday").contains("Draft updated"))
    check(stored==fit && writes==0)
    check(command("save aid setup").contains("review aid setup"))
    check(command("review aid setup","watch").contains("device"))
    check(command("review aid setup").contains("everyday"))
    check(command("save aid setup","watch").contains("device"))
    check(command("set control address to 256").contains("zero to 255"))
    check(command("set control address to two hundred fifty five").contains("Draft updated"))
    check(command("set left battery address to twelve").contains("Draft updated"))
    check(command("set right battery address to minus twelve").contains("Setup:"))
    check(command("set control address to 13").contains("Draft updated"))
    check(command("save aid setup").contains("review aid setup"))
    check(command("review aid setup").contains("address 13"))
    allow=false
    check(command("save aid setup").contains("could not be saved"));check(writes==0)
    allow=true
    check(command("save aid setup").contains("setup saved"));check(writes==1)
    check(stored!!.controlAddress==13 && stored!!.programs.single().name=="everyday")
    check(!flow.pending)
    command("configure hearing aids")
    check(command("volume ten").contains("Nothing sent"));check(writes==1)
    check(command("review aid setup",now=400_000).contains("expired"));check(!flow.pending)
    command("configure hearing aids")
    stored=fit
    check(command("save aid setup").contains("changed elsewhere"));check(writes==1)
    flow.stage(fit,stored,"watch",10)
    check(command("save aid setup").contains("device"))
    check(command("save aid setup","watch").contains("saved"));check(writes==2)
    command("configure hearing aids")
    check(command("name program two as universal").contains("unique"))
    check(command("name program two as TV and sleep").contains("unambiguous"))
    check(command("cancel aid setup").contains("cancelled"));check(!flow.pending)
    stored=null
    command("configure hearing aids")
    check(command("review aid setup").contains("control address"))
    for(text in listOf("set control address to 12","set left battery address to 12","set right battery address to 12","name program one as Universal","set tinnitus support for program one to supported"))
        check(command(text).contains("Draft updated"))
    check(command("review aid setup").contains("tinnitus supported"))
    check(command("save aid setup").contains("saved"));check(stored==fit.copy(programs=listOf(fit.programs.single().copy(name="universal"))))
    check(command("help").contains("verified"))
    check(DataVoice.parse("import aid setup")==DataVoice.IMPORT_FITTING)
    check(DataVoice.parse("export phrases?")==DataVoice.EXPORT_PHRASES)
    check(DataVoice.parse("do not import phrases")==null)
    check(command("volume ten").isEmpty())
    println("PASS: guided fitting drafts, review, origin, expiry, import staging, storage failure and fresh setup")
}
