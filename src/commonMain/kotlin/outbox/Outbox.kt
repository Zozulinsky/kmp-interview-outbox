package outbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Message(val id: String, val text: String)
data class OutboxState(val pending: List<Message>, val sending: Boolean, val error: String? = null)

interface OutboxStorage {
    fun read(): List<Message>
    fun write(messages: List<Message>)
}

interface OutboxTransport {
    suspend fun send(text: String, key: String)
}

class Outbox(private val storage: OutboxStorage, private val transport: OutboxTransport) {
    private val mutableState = MutableStateFlow(OutboxState(storage.read(), false))
    internal val state: StateFlow<OutboxState> = mutableState.asStateFlow()
    private var sending = false
    private var attempt = 0L

    fun enqueue(id: String, text: String) {
        storage.write(storage.read() + Message(id, text))
        publish()
    }

    suspend fun flush() {
        if (sending) return
        sending = true
        publish()
        try {
            while (storage.read().isNotEmpty()) {
                val message = storage.read().first()
                storage.write(storage.read().drop(1))
                publish()
                val result = runCatching {
                    transport.send(message.text, "${message.id}:${attempt++}")
                }
                if (result.isFailure) {
                    storage.write(listOf(message) + storage.read())
                    mutableState.value = OutboxState(storage.read(), false, result.exceptionOrNull()?.message)
                    return
                }
            }
        } finally {
            sending = false
            mutableState.value = mutableState.value.copy(pending = storage.read(), sending = false)
        }
    }

    private fun publish() {
        mutableState.value = OutboxState(storage.read(), sending)
    }
}
