package com.heysig.control

fun main() {
    fittingWorkflowCheck()
    phraseImportCheck()
    val fitting = Fitting(12,12,12,listOf(
        Program(1,"Universal",TinnitusCapability.SUPPORTED),
        Program(2,"Sleep",TinnitusCapability.UNKNOWN),
        Program(5,"TV",TinnitusCapability.UNSUPPORTED)))
    for(text in listOf("refresh", "refresh settings", "Hey Sig refresh hearing aids")) {
        check(VoiceCommands.parse(text,fitting)==AppIntent.Refresh)
    }
    check(VoiceCommands.parse("do not refresh",fitting) is AppIntent.Unsupported)
    check(VoiceCommands.parse("refresh and volume ten",fitting) is AppIntent.Unsupported)
    fun rejected(request: ManualRequest, state: RequestedState) =
        runCatching { ManualValidation.validate(request,fitting,state) }.isFailure
    val volume = ManualRequest.SetLevel(LevelControl.VOLUME,8)
    check(rejected(volume,RequestedState()))
    val universal = RequestedState().completed(ManualRequest.SelectProgram(1))
    ManualValidation.validate(volume,fitting,universal)
    val updated = universal.completed(volume)
    check(updated.levels[LevelControl.VOLUME] == 8)
    check(updated.completed(ManualRequest.SelectProgram(2)).levels.isEmpty())
    check(rejected(ManualRequest.SelectProgram(4),universal))
    check(rejected(ManualRequest.SetLevel(LevelControl.VOLUME,16),universal))
    val tin = ManualRequest.SetLevel(LevelControl.TINNITUS,4)
    check(!rejected(tin,universal))
    check(rejected(tin,RequestedState(2)))
    check(rejected(tin,RequestedState(5)))
    check(updated.completed(ManualRequest.Battery(true,false)) == updated)
    check(rejected(ManualRequest.Battery(false,false),universal))
    check(runCatching { fitting.copy(programs=fitting.programs + fitting.programs[0]) }.isFailure)
    check(runCatching { fitting.copy(controlAddress=256) }.isFailure)
    check(runCatching { fitting.copy(programs=listOf(Program(1,"TV",TinnitusCapability.UNKNOWN),Program(2,"tv",TinnitusCapability.UNKNOWN))) }.isFailure)
    fun parse(text: String) = VoiceCommands.parse(text,fitting)
    check(parse("SIG program too.") == AppIntent.Control(ManualRequest.SelectProgram(2)))
    check(parse("change program to TV") == AppIntent.Control(ManualRequest.SelectProgram(5)))
    check(parse("current program?") == AppIntent.ReadProgram)
    check(parse("balance negative three") == AppIntent.Control(ManualRequest.SetLevel(LevelControl.SHARPNESS,-3)))
    check(parse("change volume to fifteen") == AppIntent.Control(ManualRequest.SetLevel(LevelControl.VOLUME,15)))
    check(parse("volume sixteen") is AppIntent.Unsupported)
    check(parse("program two!!") is AppIntent.Unsupported)
    check(parse("volume up") == AppIntent.RelativeVolume(1))
    for(text in listOf("volume down","please turn the volume down","could you lower the volume?")) check(parse(text)==AppIntent.RelativeVolume(-1))
    for(text in listOf("turn up the volume","please increase my volume","can you raise the volume?")) check(parse(text)==AppIntent.RelativeVolume(1))
    for(text in listOf("don't turn volume up","volume up and down","volume up two","volume down by five","increase volume then program two")) check(parse(text) is AppIntent.Unsupported)
    for(baseline in 0..15) {
        val state=RequestedState(1,mapOf(LevelControl.VOLUME to baseline))
        for(delta in listOf(-1,1)) {
            val result=runCatching { VolumeSteps.request(delta,state,fitting) }
            if(baseline+delta in 0..15) check(result.getOrThrow()==ManualRequest.SetLevel(LevelControl.VOLUME,baseline+delta))
            else check(result.isFailure)
        }
    }
    for(state in listOf(RequestedState(),RequestedState(1),RequestedState(6,mapOf(LevelControl.VOLUME to 8)))) check(runCatching { VolumeSteps.request(1,state,fitting) }.isFailure)
    check(runCatching { VolumeSteps.request(2,RequestedState(1,mapOf(LevelControl.VOLUME to 8)),fitting) }.isFailure)
    check(VoiceCommands.parse("volume up",fitting.copy(programs=listOf(Program(1,"volume up",TinnitusCapability.UNKNOWN))))==AppIntent.Control(ManualRequest.SelectProgram(1)))
    for ((label, control) in listOf("balance" to LevelControl.SHARPNESS, "tinnitus" to LevelControl.TINNITUS, "tin" to LevelControl.TINNITUS)) {
        for ((direction, delta) in listOf("up" to 1, "down" to -1)) {
            for (phrase in listOf("$label $direction", "please turn the $label $direction", "can you turn $direction my $label?"))
                check(parse(phrase) == AppIntent.RelativeLevel(control, delta))
            for (baseline in control.range) {
                val state = RequestedState(1, mapOf(control to baseline))
                val result = runCatching { LevelSteps.request(control, delta, state, fitting) }
                if (baseline + delta in control.range)
                    check(result.getOrThrow() == ManualRequest.SetLevel(control, baseline + delta))
                else check(result.isFailure)
                check(state.levels[control] == baseline)
            }
        }
        for (phrase in listOf("do not $label up", "$label up two", "$label down by five", "$label up and volume down"))
            check(parse(phrase) is AppIntent.Unsupported)
        check(VoiceCommands.parse("$label up", fitting.copy(programs=listOf(Program(1,"$label up",TinnitusCapability.SUPPORTED)))) is AppIntent.Control)
        for (state in listOf(RequestedState(), RequestedState(1), RequestedState(6,mapOf(control to 3))))
            check(runCatching { LevelSteps.request(control,1,state,fitting) }.isFailure)
        for (delta in listOf(0,2,-2)) check(runCatching { LevelSteps.request(control,delta,RequestedState(1,mapOf(control to 3)),fitting) }.isFailure)
        check(runCatching { LevelSteps.request(control,-1,RequestedState(1,mapOf(control to control.range.last+1)),fitting) }.isFailure)
    }
    for (slot in listOf(2,5)) check(runCatching {
        LevelSteps.request(LevelControl.TINNITUS,1,RequestedState(slot,mapOf(LevelControl.TINNITUS to 3)),fitting)
    }.isFailure)
    check(parse("cancel") == AppIntent.Cancel)
    check(parse("check right battery") == AppIntent.Control(ManualRequest.Battery(false,true)))
    check(parse("current tin") == AppIntent.ReadLevel(LevelControl.TINNITUS))
    var phraseCases = 0
    val words = listOf("zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen")
    fun expect(text: String, expected: AppIntent) {
        check(parse(text) == expected) { "Wrong interpretation for: $text -> ${parse(text)}" }
        phraseCases++
    }
    for((label,control) in listOf("volume" to LevelControl.VOLUME,"tin" to LevelControl.TINNITUS,"tinnitus" to LevelControl.TINNITUS)) {
        for(level in 0..15) for(target in listOf(level.toString(),words[level])) {
            for(prefix in listOf("", "set ", "change ")) {
                expect("$prefix$label to $target", AppIntent.Control(ManualRequest.SetLevel(control,level)))
            }
        }
        expect("what is the current $label level?",AppIntent.ReadLevel(control))
    }
    for(label in listOf("balance","sharpness","sound sharpness")) {
        for(level in -8..7) {
            expect("$label $level",AppIntent.Control(ManualRequest.SetLevel(LevelControl.SHARPNESS,level)))
            val target = if(level < 0) "negative ${words[-level]}" else words[level]
            expect("change $label to $target",AppIntent.Control(ManualRequest.SetLevel(LevelControl.SHARPNESS,level)))
        }
    }
    expect("  SIG3   change  volume   to  eight! ", AppIntent.Control(volume))
    expect("sound sharpness minus four", AppIntent.Control(ManualRequest.SetLevel(LevelControl.SHARPNESS,-4)))
    for(phrase in listOf("balance minus negative three","volume four or fourteen","balance minus -3","tin sixteen","balance minus nine","volume -1","volume 1.5","program seven")) {
        check(parse(phrase) is AppIntent.Unsupported) { "Ambiguous/invalid phrase accepted: $phrase" }; phraseCases++
    }
    for(phrase in listOf("maximum tinnitus","tin max","tinnitus maximum")) expect(phrase,AppIntent.Control(ManualRequest.SetLevel(LevelControl.TINNITUS,15)))
    expect("save settings",AppIntent.SaveSettings)
    for(phrase in listOf("what is saved?","read saved settings","what settings are saved","show saved settings")) expect(phrase,AppIntent.ReadSavedSettings)
    expect("restore settings",AppIntent.RestoreSettings)
    expect("stop",AppIntent.Cancel)
    for(phrase in listOf("program number two","mode slot 2","program #2","set program two","program to Sleep")) expect(phrase,AppIntent.Control(ManualRequest.SelectProgram(2)))
    expect("program to",AppIntent.ClarifyProgram(2))
    expect("program to two",AppIntent.Control(ManualRequest.SelectProgram(2)))
    check(VoiceCommands.parse("cancel!",fitting.copy(programs=listOf(Program(1,"Cancel",TinnitusCapability.UNKNOWN)))) == AppIntent.Cancel)
    for (phrase in listOf("please switch to TV", "could you please go to the program TV?", "Hey Sig switch to TV please", "change my program to TV"))
        expect(phrase, AppIntent.Control(ManualRequest.SelectProgram(5)))
    expect("can you set my volume to eight please?", AppIntent.Control(volume))
    expect("please change the sound sharpness level to negative three", AppIntent.Control(ManualRequest.SetLevel(LevelControl.SHARPNESS,-3)))
    expect("what's my volume?", AppIntent.ReadLevel(LevelControl.VOLUME))
    expect("which program am I using?", AppIntent.ReadProgram)
    expect("could you check my batteries?", AppIntent.Control(ManualRequest.Battery(true,true)))
    expect("please restore my settings", AppIntent.RestoreSettings)
    expect("could you please stop", AppIntent.Cancel)
    for (phrase in listOf("don't switch to TV", "please do not set my volume to eight", "switch to TV and Sleep", "set my volume to eight or nine", "please set my volume to sixteen", "turn it up", "could you switch to TV then mute", "please switch to TV!!")) {
        check(parse(phrase) is AppIntent.Unsupported) { "Unsafe natural phrase accepted: $phrase" }
        phraseCases++
    }
    var durable = emptyList<PersonalPhrase>()
    var storageWorks = true
    val learning = PersonalPhrases { next -> if(storageWorks) { durable = next; true } else false }
    var now = 1000L
    fun manage(text: String, origin: String = "phone", key: String = "setup1") = learning.manage(text,fitting,key,origin,now)
    fun learned(text: String, key: String = "setup1") = learning.resolve(text,fitting,key)
    check(manage("learn phrase TV time means program TV")!!.contains("Remember"))
    check(durable.isEmpty())
    check(manage("yes")!!.contains("cancelled"))
    check(durable.isEmpty())
    manage("learn phrase TV time means program TV")
    check(manage("remember that correction", "watch")!!.contains("device"))
    check(manage("remember that correction")!!.contains("Remembered"))
    check(durable.size == 1 && durable.single().confirmedAt == now)
    check(learned("TV time!") == AppIntent.Control(ManualRequest.SelectProgram(5)))
    check(learned("TV time", "setup2") is AppIntent.Unsupported)
    check(PersonalPhrases(durable) { true }.resolve("TV time",fitting,"setup1") == learned("TV time"))
    check(learned("TV time and Sleep") is AppIntent.Unsupported)
    check(manage("learn phrase TV time means program Sleep")!!.contains("already"))
    for(phrase in listOf("stop", "cancel", "volume sixteen", "volume eight", "TV or Sleep", "don't switch", "maybe negative three", "yes", "reset learned phrases")) {
        check(manage("learn phrase $phrase means program TV")!!.contains("reserved")) { phrase }
    }
    check(manage("learn phrase bad target means volume sixteen")!!.contains("valid"))
    manage("learn phrase quiet time means program Sleep")
    now += 60_001
    check(manage("remember that correction")!!.contains("expired"))
    manage("learn phrase quiet time means program Sleep")
    check(manage("remember that correction",key="setup2")!!.contains("setup changed"))
    manage("learn phrase quiet time means program Sleep")
    manage("cancel")
    check(manage("remember that correction")!!.contains("No correction"))
    storageWorks = false
    check(manage("forget phrase TV time")!!.contains("Could not save"))
    check(durable.size == 1 && learning.records.size == 1)
    storageWorks = true
    check(manage("forget phrase TV time")!!.contains("Forgot"))
    check(learned("TV time") is AppIntent.Unsupported)
    manage("learn phrase TV time means program TV"); manage("remember that correction")
    manage("reset learned phrases"); manage("yes")
    check(learning.records.size == 1)
    manage("reset learned phrases"); manage("confirm reset learned phrases")
    check(learning.records.isEmpty() && durable.isEmpty())
    val fullStore = PersonalPhrases((1..200).map { PersonalPhrase("$it","phrase $it","program TV","setup1","phone",now) }) { error("Must not evict records") }
    check(fullStore.manage("learn phrase television time means program TV",fitting,"setup1","phone",now)!!.contains("full"))
    check(fullStore.manage("list my phrases page 20",fitting,"setup1","phone",now)!!.contains("Page 20 of 20"))
    check(fullStore.manage("list my phrases page 99999999999999",fitting,"setup1","phone",now)!!.contains("Choose"))
    val protected=RequestedState(1,mapOf(LevelControl.VOLUME to 6))
    val captured=SavedSettings.snapshot(protected,fitting)
    check(SavedSettings.plan(captured,fitting)==listOf(ManualRequest.SelectProgram(1),ManualRequest.SetLevel(LevelControl.VOLUME,6)))
    for(state in listOf(RequestedState(),RequestedState(6),RequestedState(5,mapOf(LevelControl.TINNITUS to 4))))
        check(runCatching { SavedSettings.snapshot(state,fitting) }.isFailure)
    check(SavedSettings.plan(RequestedState(2),fitting)==listOf(ManualRequest.SelectProgram(2)))
    val session = ManualSession(fitting)
    check(runCatching { session.restorePlan() }.isFailure)
    session.completed(ManualRequest.SelectProgram(1)); session.completed(volume)
    session.completed(ManualRequest.SetLevel(LevelControl.SHARPNESS,-3))
    session.save()
    session.completed(ManualRequest.SelectProgram(5))
    val plan = session.restorePlan()
    check(plan.size == 3 && plan[0] == ManualRequest.SelectProgram(1))
    check(session.requested.program == 5) // Planning cannot claim transmission.
    plan.forEach { session.completed(it) }
    check(session.requested == session.snapshot)
    check(VoicePreference.parse("voice prompts off") == VoicePreference(true,false))
    check(VoicePreference.parse("Turn spoken responses on!") == VoicePreference(false,true))
    check(VoicePreference.parse("set voice responses off") == VoicePreference(false,false))
    check(VoicePreference.parse("turn off voice prompts") == VoicePreference(true,false))
    check(VoicePreference.parse("turn on spoken responses") == VoicePreference(false,true))
    check(VoicePreference.parse("voice prompts on off") == null)
    check(VoicePreference.parse("volume off") == null)
    println("PASS: $phraseCases voice phrase cases plus fitting, context, ranges, tinnitus, restore planning voice preferences and personal phrase consent/persistence/scoping/deletion guards")
}
