package com.heysig.control

/** A reviewed, origin-bound, expiring setup draft. Never executes aid commands. */
class FittingWorkflow(private val save: (Fitting) -> Boolean) {
    private data class Draft(val original: Fitting?, val origin: String, val expires: Long,
        var control: Int?, var left: Int?, var right: Int?, val programs: MutableMap<Int, Program>, var reviewed: Boolean = false)
    private var draft: Draft? = null
    val pending get() = draft != null
    fun cancel() { draft = null }
    fun isPromptFor(origin: String) = draft?.origin == origin
    companion object {
        fun normalize(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ").replace(Regex("[.!?]$"), "")
        private fun addressNumber(text: String): Int {
            text.toIntOrNull()?.let { require(it in 0..255) { "Address must be zero to 255" }; return it }
            val small=listOf("zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen")
            val tens=listOf("twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety")
            fun underHundred(words: String): Int? {
                small.indexOf(words).takeIf { it >= 0 }?.let { return it }
                val parts=words.split(" ")
                val ten=tens.indexOf(parts[0]).takeIf { it >= 0 }?.let { (it+2)*10 } ?: return null
                return if(parts.size==1) ten else if(parts.size==2) small.indexOf(parts[1]).takeIf { it in 1..9 }?.let { ten+it } else null
            }
            val words=text.replace("-"," ")
            val hundreds=Regex("^(one|two) hundred(?: (.+))?$").matchEntire(words)
            val number=if(hundreds != null) {
                val rest=hundreds.groupValues[2]
                (if(hundreds.groupValues[1]=="one") 100 else 200) +
                    if(rest.isEmpty()) 0 else requireNotNull(underHundred(rest)) { "Use one address number" }
            } else requireNotNull(underHundred(words)) { "Use one address number from zero to 255" }
            require(number in 0..255) { "Address must be zero to 255" }
            return number
        }
        fun accepts(text: String): Boolean {
            val input = normalize(text)
            return input in listOf("configure hearing aids", "review aid setup", "save aid setup", "cancel aid setup", "setup help", "help") ||
                Regex("^(set (?:control|left battery|right battery) address to |name program |set tinnitus support for program |remove program )").containsMatchIn(input)
        }
    }
    fun stage(fitting: Fitting, current: Fitting?, origin: String, now: Long): String {
        check(draft == null || draft?.origin == origin) { "Finish or cancel setup on the device that started it." }
        draft = Draft(current,origin,now+300_000,fitting.controlAddress,fitting.leftAddress,fitting.rightAddress,
            fitting.programs.associateBy { it.slot }.toMutableMap())
        return "Imported draft only. ${review(requireNotNull(draft))}"
    }
    private fun fitting(d: Draft) = Fitting(requireNotNull(d.control) { "Set the control address" },
        requireNotNull(d.left) { "Set the left battery address" }, requireNotNull(d.right) { "Set the right battery address" },d.programs.values.sortedBy { it.slot })
    private fun review(d: Draft): String {
        val fit = fitting(d)
        d.reviewed = true
        return "Draft aid setup. Control address ${fit.controlAddress}; left battery ${fit.leftAddress}; right battery ${fit.rightAddress}. " +
            fit.programs.joinToString(". ") { "Program ${it.slot}: ${it.name}; tinnitus ${it.tinnitus.name.lowercase()}" } +
            ". Saving a changed setup archives previous settings and clears active requested/restoration assumptions; learned phrases and dependent routines need review. Say save aid setup or cancel aid setup. Nothing sent."
    }
    fun handle(text: String, current: Fitting?, origin: String, now: Long): String? {
        val input = normalize(text)
        if(input in listOf("stop","cancel","cancel aid setup")) {
            if(draft != null && draft?.origin != origin) return "Cancel setup on the device that started it."
            val existed = pending; cancel()
            return if(existed || input == "cancel aid setup") "Aid setup cancelled. Nothing saved or sent." else null
        }
        if(input in listOf("help","setup help")) return "Say configure hearing aids, set control address to a verified number, set left battery address to a number, set right battery address to a number, name program one as Universal, set tinnitus support for program one to supported, review aid setup, then save aid setup. Unknown addresses are never guessed. Program slots are one to six. Use cancel aid setup to discard. For everyday control say a program name, Balance up, tinnitus down, current volume, save settings or restore settings."
        if(!accepts(input)) {
            if(pending) return "Aid setup is awaiting review. Say review aid setup, save aid setup or cancel aid setup. Nothing sent."
            return null
        }
        if(draft != null && draft?.origin != origin) return "Finish or cancel setup on the device that started it."
        if(draft != null && (now > draft!!.expires || current != draft!!.original)) {
            cancel(); return "Aid setup expired or changed elsewhere. Nothing saved. Say configure hearing aids to start again."
        }
        if(input == "configure hearing aids") {
            if(draft != null) return "A draft is already open. Review or cancel it before starting again."
            draft = Draft(current,origin,now+300_000,current?.controlAddress,current?.leftAddress,current?.rightAddress,
                current?.programs?.associateBy { it.slot }?.toMutableMap() ?: mutableMapOf())
            return "Aid setup draft started. Nothing sent. ${if(current == null) "Supply verified addresses and program names; no defaults are assumed." else "Existing fitting copied to draft."} Say setup help for commands, review aid setup before saving, or cancel aid setup."
        }
        val d = draft ?: return "Say configure hearing aids first. Nothing changed."
        return try {
            when(input) {
                "review aid setup" -> review(d)
                "save aid setup" -> {
                    require(d.reviewed) { "Say review aid setup before saving" }
                    val fit = fitting(d)
                    if(save(fit)) { cancel(); "Aid setup saved. Nothing sent. Review dependent noise/sleep settings and learned phrases before use." }
                    else "Setup could not be saved. Previous setup retained. Review and try again or cancel."
                }
                else -> {
                    fun slot(value: String): Int = (mapOf("one" to 1,"two" to 2,"three" to 3,"four" to 4,"five" to 5,"six" to 6)[value] ?: value.toIntOrNull() ?: error("Choose program one to six")).also { require(it in 1..6) }
                    val address=Regex("^set (control|left battery|right battery) address to (.+)$").matchEntire(input)
                    val name=Regex("^name program ([a-z]+|[1-6]) as (.{1,60})$").matchEntire(input)
                    val tinnitus=Regex("^set tinnitus support for program ([a-z]+|[1-6]) to (unknown|supported|unsupported)$").matchEntire(input)
                    val remove=Regex("^remove program ([a-z]+|[1-6])$").matchEntire(input)
                    when {
                        address != null -> {
                            val number=addressNumber(address.groupValues[2])
                            when(address.groupValues[1]) { "control" -> d.control=number; "left battery" -> d.left=number; else -> d.right=number }
                        }
                        name != null -> {
                            val id=slot(name.groupValues[1]); val label=name.groupValues[2]
                            require(!Regex("\\b(and|then|not|don't)\\b").containsMatchIn(label)) { "Use one unambiguous program name" }
                            require(d.programs.values.none { it.slot != id && it.name.equals(label,true) }) { "Program names must be unique" }
                            d.programs[id]=Program(id,label,d.programs[id]?.tinnitus ?: TinnitusCapability.UNKNOWN)
                        }
                        tinnitus != null -> {
                            val id=slot(tinnitus.groupValues[1]); val program=requireNotNull(d.programs[id]) { "Name this program first" }
                            d.programs[id]=program.copy(tinnitus=TinnitusCapability.valueOf(tinnitus.groupValues[2].uppercase()))
                        }
                        remove != null -> { d.programs.remove(slot(remove.groupValues[1])) }
                        else -> error("Use setup help for exact setup commands")
                    }
                    d.reviewed=false
                    "Draft updated only. Say review aid setup before saving or cancel aid setup. Nothing sent."
                }
            }
        } catch(e: Exception) { "Setup: ${e.message}. Nothing saved or sent." }
    }
}
