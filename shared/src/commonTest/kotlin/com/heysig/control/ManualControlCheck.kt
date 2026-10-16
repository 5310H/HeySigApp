package com.heysig.control

fun main() {
    val fitting = Fitting(12,12,12,listOf(
        Program(1,"Universal",TinnitusCapability.SUPPORTED),
        Program(2,"Sleep",TinnitusCapability.UNKNOWN),
        Program(5,"TV",TinnitusCapability.UNSUPPORTED)))
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
    check(parse("volume up") is AppIntent.Unsupported)
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
    expect("restore settings",AppIntent.RestoreSettings)
    expect("stop",AppIntent.Cancel)
    for(phrase in listOf("program number two","mode slot 2","program #2","set program two","program to Sleep")) expect(phrase,AppIntent.Control(ManualRequest.SelectProgram(2)))
    expect("program to",AppIntent.ClarifyProgram(2))
    expect("program to two",AppIntent.Control(ManualRequest.SelectProgram(2)))
    check(VoiceCommands.parse("cancel!",fitting.copy(programs=listOf(Program(1,"Cancel",TinnitusCapability.UNKNOWN)))) == AppIntent.Cancel)
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
    println("PASS: $phraseCases voice phrase cases plus fitting, context, ranges, tinnitus, restore planning and voice preferences")
}
