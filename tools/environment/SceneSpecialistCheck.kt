package com.heysig.tools

import com.heysig.noise.ListeningEnvironment

fun main() {
    val small = ListeningEnvironment.SMALL_GROUP
    for (environment in ListeningEnvironment.entries) {
        check(smallGroupFallback(environment to .8, null to 0.0) == (environment to .8))
        check(smallGroupFallback(null to 0.0, environment to .9) == if (environment == small) small to .9 else null to 0.0)
        check(smallGroupFallback(environment to .8, small to .65) == if (environment == small) small to .8 else null to 0.0)
        check(smallGroupFallback(environment to .8, small to .649999) == (environment to .8))
        check(smallGroupFallback(environment to .649999, small to .65) == (small to .65))
        for (other in ListeningEnvironment.entries.filter { it != small }) {
            check(smallGroupFallback(environment to .8, other to .99) == (environment to .8))
        }
    }
    check(smallGroupFallback(null to .99, small to .649999) == (null to 0.0))
    check(smallGroupFallback(small to .65, small to .99) == (small to .65))
    for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.1, 1.01)) {
        check(runCatching { smallGroupFallback(null to invalid, small to .8) }.isFailure)
        check(runCatching { smallGroupFallback(null to 0.0, small to invalid) }.isFailure)
    }
    println("PASS: primary preservation, all six specialist labels, conflicts, exact threshold, confidence and invalid scores.")
}
