package com.bamnat.app.money

/**
 * 2.0.0 [RVK-43] (검토 MONEY-34) The end of a money notice's receiver: plan the next one, then let the broadcast go. Before, a
 * plan that threw skipped `finish()` and the phone held the receiver until it gave up on it.
 */
internal object ReceiverSteps {
    inline fun thenFinish(next: () -> Unit, finish: () -> Unit) {
        try {
            next()
        } finally {
            finish()
        }
    }
}
