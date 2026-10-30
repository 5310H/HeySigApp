package com.heysig.companion

fun main() {
    val id = "00000000-0000-0000-0000-000000000001"
    val second = "00000000-0000-0000-0000-000000000002"
    var sequence = 0
    val gate = CommandLeaseGate("watch") { "nonce-${++sequence}" }
    fun rejects(action: () -> Unit) = check(runCatching(action).isFailure)
    rejects { gate.issue("other",id,0) }
    val token = gate.issue("watch",id,0)
    rejects { gate.consume("other",id,token,1) }
    rejects { gate.consume("watch",id,"wrong",1) }
    gate.consume("watch",id,token,4999)
    rejects { gate.consume("watch",id,token,4999) }
    rejects { gate.issue("watch",id,5000) }
    val expired = gate.issue("watch",second,5000)
    rejects { gate.consume("watch",second,expired,10000) }
    gate.close()
    rejects { gate.issue("watch",second,10001) }
    val reconnect = CommandLeaseGate("watch") { "fresh" }
    val monitoring = CommandLeaseGate("watch",4096) { "monitoring-${++sequence}" }
    // One-hour 15-second sampling must not exhaust the manual connection's 128 grants.
    repeat(242) { i ->
        val sampleId="00000000-0000-0000-0000-"+i.toString().padStart(12,'0')
        val sampleToken=monitoring.issue("watch",sampleId,i*1000L)
        monitoring.consume("watch",sampleId,sampleToken,i*1000L+1)
    }
    rejects { monitoring.issue("watch",id,300_000) }
    reconnect.issue("watch",second,10001)
    rejects { reconnect.consume("watch",second,expired,10002) }
    CompanionSafety.command(id,second,"status",listOf("status"))
    rejects { CompanionSafety.command(id,second,"volume 8",listOf("volume 9")) }
    rejects { CompanionSafety.command(id,second,"x".repeat(513),listOf("x".repeat(513))) }
    rejects { CompanionSafety.command("invalid",second,"status",listOf("status")) }
    rejects { CompanionSafety.command(id,second,"status",List(6) { "status" }) }
    check(CompanionSafety.retained(mapOf("old" to 0L,"new" to 86400000L,"future" to 86400002L),86400001L) == setOf("new"))
    check(CompanionSafety.retained(mapOf("a" to 2L,"b" to 1L),3,1) == setOf("a"))
    println("PASS: companion sender binding, one-use/expired leases, reconnect, bounded commands and history retention")
}
