package com.bamnat.app.watch

import android.content.Context
import com.bamnat.core.work.WatchDays
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [WEAR-06] 1.31 N2 워치 진동 알람: tells the paired watches that the phone's alarm rings ([WatchDays.RING_PATH], with what
 * it is for) and that it stopped ([WatchDays.RING_STOP_PATH]). Phone to watch over the Data Layer only; nothing goes to
 * the app's servers. The watch keeps no alarm of its own: with no watch near, nothing is sent and the phone rings.
 */
object WatchRing {
    /** How long to look for a watch before the phone gives up and rings by itself. */
    private const val REACH_MILLIS = 5_000L

    /** True when at least one watch took the message. */
    suspend fun ring(context: Context, what: String): Boolean = send(context, WatchDays.RING_PATH, what)

    suspend fun stop(context: Context) {
        send(context, WatchDays.RING_STOP_PATH, "")
    }

    private suspend fun send(context: Context, path: String, text: String): Boolean = withTimeoutOrNull(REACH_MILLIS) {
        val nodes = runCatching { await(Wearable.getNodeClient(context).connectedNodes) }.getOrNull().orEmpty()
        var sent = false
        nodes.forEach { node ->
            if (runCatching { await(Wearable.getMessageClient(context).sendMessage(node.id, path, text.toByteArray())) }.getOrNull() != null) sent = true
        }
        sent
    } ?: false

    private suspend fun <T> await(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
    }
}
