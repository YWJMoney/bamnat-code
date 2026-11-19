package com.bamnat.core

/**
 * 2.0.0 [RVR-19] (검토 TEST-20 ⑤) [block]'s answer and the quickest of [tries] runs of it, in milliseconds. A speed promise checked
 * by the wall clock failed now and then on a busy CI machine (a pause for memory, another job beside it); the quickest of two keeps
 * the promise for code that is slow every time and lets one hiccup pass. [block] must give the same answer each run.
 */
internal fun <T> fastestMillis(tries: Int = 2, block: () -> T): Pair<T, Long> {
    var best = Long.MAX_VALUE
    var answer: T? = null
    repeat(tries) {
        val started = System.nanoTime()
        val out = block()
        val ms = (System.nanoTime() - started) / 1_000_000
        if (ms < best) { best = ms; answer = out }
    }
    @Suppress("UNCHECKED_CAST")
    return (answer as T) to best
}
