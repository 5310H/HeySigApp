package com.heysig.control

/** Optional manual comparison. Memory-only; no model learning, dispatch or storage. */
class ProgramComparison(val fitting: Fitting, val first: Int, val second: Int, val returnTo: Int) {
    enum class Aspect { UNDERSTANDING, EFFORT, COMFORT, AWARENESS }
    enum class Preference { FIRST, SECOND, SAME, UNSURE }
    private val answers=mutableMapOf<Aspect,Preference>()
    init {
        require(first!=second) { "Choose two different programs" }
        require(listOf(first,second,returnTo).all { slot -> fitting.programs.any { it.slot==slot } }) { "Choose configured programs" }
    }
    fun target(slot:Int,current:Fitting):ManualRequest.SelectProgram {
        require(current==fitting) { "Setup changed; review a new comparison" }
        require(slot in listOf(first,second,returnTo)) { "Program is outside this comparison" }
        return ManualRequest.SelectProgram(slot)
    }
    fun answer(aspect:Aspect,preference:Preference) { answers[aspect]=preference }
    fun answer(aspect:Aspect):Preference?=answers[aspect]
    fun clear() { answers.clear() }
}
