package outbox

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryStorage : OutboxStorage {
    private var messages = emptyList<Message>()
    override fun read(): List<Message> = messages.toList()
    override fun write(messages: List<Message>) { this.messages = messages.toList() }
}

class ControlledTransport : OutboxTransport {
    data class Call(val text: String, val key: String, val reply: CompletableDeferred<Unit>)
    val calls = mutableListOf<Call>()
    override suspend fun send(text: String, key: String) {
        val call = Call(text, key, CompletableDeferred())
        calls += call
        call.reply.await()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxTest {
    @Test
    fun successfulSend() = runTest {
        val storage = MemoryStorage()
        val transport = ControlledTransport()
        val outbox = Outbox(storage, transport)
        outbox.enqueue("m1", "hello")
        assertEquals(listOf(Message("m1", "hello")), storage.read())
        val sending = launch { outbox.flush() }
        runCurrent()
        assertEquals("hello", transport.calls.single().text)
        transport.calls.single().reply.complete(Unit)
        sending.join()
        assertEquals(emptyList(), storage.read())
    }
}
