package outbox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OutboxSubscription internal constructor(private val job: Job) {
    fun close() { job.cancel() }
}

class OutboxSnapshot(val pendingCount: Int, val sending: Boolean, val error: String?)

class OutboxFacade(private val outbox: Outbox) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun observe(onState: (OutboxSnapshot) -> Unit): OutboxSubscription {
        val job = scope.launch {
            outbox.state.collect {
                onState(OutboxSnapshot(it.pending.size, it.sending, it.error))
            }
        }
        return OutboxSubscription(job)
    }

    fun sendPending() { scope.launch { outbox.flush() } }
    fun close() { scope.cancel() }
}
