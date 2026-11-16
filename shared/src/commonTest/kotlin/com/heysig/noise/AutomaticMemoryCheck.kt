package com.heysig.noise

fun main() {
    val embedding=DoubleArray(2048) { if(it==10) 1.0 else 0.0 }
    val sound=AutomaticSoundContext.create(embedding,List(8) { .3 })!!
    val changed=AutomaticSoundContext(sound.values.take(32)+List(8) { .8 })
    var book=AutomaticMemory()
    check(book.recall("phone",sound)==null)
    book=book.remember(AutomaticExperience("phone",sound,AutomaticScene.TV,1000))
    check(book.recall("phone",sound)==AutomaticScene.TV)
    check(AutomaticMemory(listOf(AutomaticExperience("phone",sound,AutomaticScene.TV,1),AutomaticExperience("phone",sound,AutomaticScene.NORMAL,2))).recall("phone",sound)==null)
    check(book.recall("watch",sound)==null && book.recall("phone",changed)==null)
    book=book.remember(AutomaticExperience("phone",sound,AutomaticScene.NORMAL,2000))
    check(book.experiences.size==1 && book.recall("phone",sound)==AutomaticScene.NORMAL)
    for(i in 0..30) book=book.remember(AutomaticExperience("scope-"+i,sound,AutomaticScene.NOISY,i.toLong()))
    check(book.experiences.size==24 && book.recall("scope-0",sound)==null)
    check(runCatching { AutomaticSoundContext(sound.values.drop(1)) }.isFailure)
    check(runCatching { AutomaticSoundContext(sound.values.toMutableList().also { it[0]=Double.NaN }) }.isFailure)
    check(AutomaticSoundContext.create(embedding,List(8) { Double.NaN })==null)
    val model="HSGTV1\n0.2,0.8\n0.0\n"+List(2048) { "0.0" }.joinToString(",")+"\n"
    check(TelevisionHead.decode(model).presence(embedding)==TelevisionPresence.UNKNOWN)
    check(TelevisionHead.decode(model.replace("\n0.0\n","\n5.0\n")).presence(embedding)==TelevisionPresence.PRESENT)
    check(TelevisionHead.decode(model.replace("\n0.0\n","\n-5.0\n")).presence(embedding)==TelevisionPresence.ABSENT)
    check(TelevisionHead.decode(model).score(DoubleArray(2048))==null)
    check(runCatching { TelevisionHead.decode(model.replace("0.2,0.8","0.9,0.2")) }.isFailure)
    println("PASS: explicit bounded memory, source/setup isolation, reviewed replacement, invalid context rejection and TV-head validation")
}
