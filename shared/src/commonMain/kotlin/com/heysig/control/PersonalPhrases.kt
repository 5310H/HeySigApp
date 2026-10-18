package com.heysig.control

/** Explicitly approved, exact phrases. No audio, inference, execution or automatic replay. */
data class PersonalPhrase(val id: String, val phrase: String, val target: String,
    val fittingKey: String, val source: String, val confirmedAt: Long, val schemaVersion: Int = 1)

class PersonalPhrases(
    initial: List<PersonalPhrase> = emptyList(),
    private val persist: (List<PersonalPhrase>) -> Boolean
) {
    var records: List<PersonalPhrase> = initial.toList(); private set
    private data class Draft(val phrase: String?, val target: String?, val fittingKey: String,
        val origin: String, val deadline: Long)
    private var draft: Draft? = null
    private data class ImportDraft(val entries: List<Pair<String,String>>,val key: String,val origin: String,val deadline: Long)
    private var imported: ImportDraft? = null
    fun stageImport(entries: List<Pair<String,String>>, fit: Fitting, key: String, origin: String, now: Long,
        reserved: (String) -> Boolean = { false }): String {
        require(draft == null && imported == null) { "Finish or cancel the existing phrase review first" }
        require(entries.isNotEmpty() && records.size + entries.size <= 200) { "Import must fit the 200 phrase limit" }
        val probe=PersonalPhrases(records) { false }
        val checked=entries.map { (phrase,target) ->
            val response=probe.manage("learn phrase $phrase means $target",fit,key,origin,now,reserved)
            val proposed=probe.draft
            require(proposed?.phrase != null && proposed.target != null) { response ?: "Invalid phrase" }
            require(!phrase.contains("\n") && !target.contains("\n")) { "Use single-line phrases" }
            proposed.phrase to proposed.target
        }
        require(checked.map { it.first }.distinct().size == checked.size) { "Duplicate import phrases" }
        imported=ImportDraft(checked,key,origin,now+300_000)
        return "Review ${checked.size} imported phrases. " + checked.joinToString(". ") { "${it.first} means ${it.second}" } +
            ". Say confirm phrase import or cancel. Nothing saved or sent."
    }
    val awaitingConfirmation: Boolean get() = draft != null || imported != null
    fun isPromptFor(origin: String) = draft?.origin == origin || imported?.origin == origin
    fun cancel() { draft = null; imported = null }
    fun normalize(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ").replace(Regex("[.!?]$"), "")
    private fun allowed(intent: AppIntent) = intent is AppIntent.Control || intent is AppIntent.ReadLevel ||
        intent == AppIntent.ReadProgram || intent == AppIntent.ListPrograms
    private fun reservedPhrase(text: String): Boolean =
        text in listOf("yes", "no", "confirm", "send again", "status") ||
        Regex("^(learn|forget|remember|reset|confirm|list my|volume|balance|sharpness|sound sharpness|tin|tinnitus|program|mode)( |$)").containsMatchIn(text) ||
        Regex("\\b(and|or|then|not|never|no|don't|zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|minus|negative)\\b|[0-9]").containsMatchIn(text)
    fun resolve(text: String, fit: Fitting, key: String): AppIntent {
        if(reservedPhrase(normalize(text))) return VoiceCommands.parse(text,fit)
        val exact = VoiceCommands.parse(text, fit)
        if(exact !is AppIntent.Unsupported) return exact
        val matches = records.filter { it.schemaVersion == 1 && it.fittingKey == key && it.phrase == normalize(text) }
        if(matches.size != 1) return exact
        return VoiceCommands.parse(matches.single().target, fit).takeIf { allowed(it) } ?: exact
    }
    /** Management consumes its input; even confirmation only stores a mapping. */
    fun manage(text: String, fit: Fitting, key: String, origin: String, now: Long,
        reserved: (String) -> Boolean = { false }): String? {
        var input = normalize(text)
        val conversational=Regex("^when i say (.+) i mean (.+)$").matchEntire(input)
        if(conversational != null) input="learn phrase ${conversational.groupValues[1]} means ${conversational.groupValues[2]}"
        if(input in listOf("stop", "cancel")) { cancel(); return null }
        imported?.let { pending ->
            if(pending.origin != origin) return "Finish phrase import on the device that started it."
            imported=null
            if(now > pending.deadline || key != pending.key) return "Phrase import expired or setup changed. Nothing saved."
            if(input != "confirm phrase import") return "Phrase import cancelled. Nothing saved or sent. Repeat your command separately."
            val next=pending.entries.mapIndexed { index,(phrase,target) ->
                PersonalPhrase("$now-import-$index",phrase,target,key,"$origin-import",now)
            }
            return save(records+next,"Imported ${next.size} reviewed phrases. Nothing sent. Say a phrase separately to use it.")
        }
        if(input == "confirm phrase import") return "No phrase import waiting for review."
        val listing = Regex("^list my (?:corrections|phrases)(?: page ([0-9]+))?$").matchEntire(input)
        if(listing != null) {
            if(records.isEmpty()) return "No learned phrases."
            val pages = (records.size + 9) / 10
            val page = listing.groupValues[1].ifEmpty { "1" }.toIntOrNull()
            if(page == null || page !in 1..pages) return "Choose a phrase page from 1 to $pages."
            return "Page $page of $pages. " + records.drop((page-1)*10).take(10).joinToString(". ") {
                "${it.phrase} means ${it.target}${if(it.fittingKey != key) " (inactive: setup changed)" else ""}"
            } + if(page < pages) ". Say list my phrases page ${page+1} for more." else ""
        }
        if(input.startsWith("forget phrase ")) {
            cancel()
            val phrase = normalize(input.removePrefix("forget phrase "))
            val next = records.filterNot { it.phrase == phrase }
            return if(next.size == records.size) "No matching learned phrase." else save(next, "Forgot $phrase. Nothing sent.")
        }
        if(input == "reset learned phrases") {
            draft = Draft(null, null, key, origin, now + 60_000)
            return "Delete all learned phrases? Say confirm reset learned phrases or cancel."
        }
        if(input in listOf("remember that correction", "confirm reset learned phrases")) {
            val pending = draft ?: return "No correction waiting for confirmation."
            if(pending.origin != origin) return "Confirm or cancel on the device that started learning."
            draft = null
            if(now > pending.deadline || key != pending.fittingKey) return "Learning confirmation expired or setup changed. Nothing saved."
            if(pending.phrase == null) {
                if(input != "confirm reset learned phrases") return "Reset cancelled. Nothing deleted."
                return save(emptyList(), "All learned phrases deleted. Nothing sent.")
            }
            if(input != "remember that correction") return "Learning cancelled. Nothing saved."
            if(records.size >= 200) return "Phrase store is full. Forget a phrase before adding another."
            val record = PersonalPhrase("$now-${records.size}",pending.phrase,pending.target!!,key,origin,now)
            return save(records + record, "Remembered ${record.phrase} means ${record.target}. Nothing sent. Say the phrase separately to use it.")
        }
        if(input.startsWith("learn phrase ")) {
            draft = null
            val parts = input.removePrefix("learn phrase ").split(" means ")
            if(parts.size != 2) return "Say learn phrase, your phrase, means, an exact command."
            val phrase = normalize(parts[0]); val target = normalize(parts[1])
            if(phrase.isBlank() || phrase.length > 120 || target.length > 120) return "Use a phrase and command of 1 to 120 characters."
            if(reserved(phrase) || VoiceCommands.parse(phrase,fit) !is AppIntent.Unsupported ||
                reservedPhrase(phrase))
                return "That phrase is reserved or already a command. Choose a different phrase."
            if(records.any { it.phrase == phrase }) return "Phrase already exists. Review and forget it before replacing it."
            if(!allowed(VoiceCommands.parse(target,fit))) return "Use a valid program, absolute level, battery or current-value command as the meaning."
            if(records.size >= 200) return "Phrase store is full. Forget a phrase before adding another."
            draft = Draft(phrase,target,key,origin,now + 60_000)
            return "Remember $phrase as $target? This stores the phrase and meaning locally, without audio. Say remember that correction or cancel. Nothing sent; prior playback is not undone."
        }
        // Ordinary commands never imply permission to remember or erase anything.
        if(draft != null) {
            draft = null
            return "Learning cancelled. Nothing saved or sent. Repeat your command separately."
        }
        return null
    }
    private fun save(next: List<PersonalPhrase>, result: String): String {
        if(!persist(next)) return "Could not save phrase changes. Nothing changed."
        records = next.toList()
        return result
    }
}
