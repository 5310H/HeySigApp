package com.heysig.companion

/** Values are validated independently of Android, before the manual command parser. */
object CompanionSafety {
    const val PATH = "/heysig/companion/v1"
    const val CAPABILITY = "heysig_phone_v1"
    const val MAX_BYTES = 8192
    const val HISTORY_MILLIS = 24 * 60 * 60 * 1000L
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    fun id(value: String) { require(uuid.matches(value)) { "Invalid request identifier" } }
    fun command(id: String, conversation: String, text: String, alternatives: List<String>) {
        this.id(id); this.id(conversation)
        require(text.isNotBlank() && text.length <= 512) { "Command must contain 1–512 characters" }
        require(alternatives.size in 1..5 && alternatives.first() == text) { "Invalid speech alternatives" }
        require(alternatives.all { it.isNotBlank() && it.length <= 512 }) { "Invalid speech alternative" }
    }
    /** No raw speech is needed for replay protection. */
    fun retained(entries: Map<String, Long>, now: Long, limit: Int = 64): Set<String> = entries
        .filterValues { it <= now && now - it < HISTORY_MILLIS }
        .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .take(limit).map { it.key }.toSet()
}

/** A short-lived one-use permission to send one command. Never queues or retries. */
class CommandLeaseGate(private val node: String, private val nonce: () -> String) {
    private data class Lease(val token: String, val expires: Long)
    private val leases = mutableMapOf<String, Lease>()
    private val used = mutableSetOf<String>()
    private var open = true
    fun issue(sender: String, id: String, now: Long): String {
        CompanionSafety.id(id)
        check(open && sender == node) { "Watch connection is not enabled" }
        check(id !in used && used.size < 128) { "Request already used or session full; reconnect without replay" }
        leases.entries.removeAll { now >= it.value.expires }
        check(leases.size < 8) { "Too many pending requests" }
        return nonce().also { leases[id] = Lease(it, now + 5000) }
    }
    fun consume(sender: String, id: String, token: String, now: Long) {
        check(open && sender == node) { "Watch connection is not enabled" }
        val lease = leases[id] ?: error("Request expired; no command sent")
        check(token == lease.token && now < lease.expires) { "Request expired; no command sent" }
        leases.remove(id)
        check(used.add(id)) { "Request already used; no replay" }
    }
    fun close() { open = false; leases.clear(); used.clear() }
}
