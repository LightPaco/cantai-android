package com.cantai.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

object Config {
    /** Filled in once the secure backend exists. Never put API keys in the app. */
    const val BACKEND_URL: String = ""
}

enum class Sender { User, Cantai, System }

data class ChatMessage(val id: Long, val sender: Sender, val text: String)

enum class Link(val label: String) {
    NotConfigured("BRAIN NOT CONNECTED"),
    Connecting("CONNECTING"),
    Online("ONLINE"),
    Error("CONNECTION ERROR")
}

class ChatState(private val store: ChatStore) {
    val messages = mutableStateListOf<ChatMessage>()
    val conversations = mutableStateListOf<Conversation>()

    var currentId by mutableStateOf(UUID.randomUUID().toString())
        private set

    var link by mutableStateOf(
        if (Config.BACKEND_URL.isBlank()) Link.NotConfigured else Link.Connecting
    )
        private set

    var thinking by mutableStateOf(false)
        private set

    var saveFailed by mutableStateOf(false)
        private set

    /** Called for every Cantai or system message so the voice layer can speak it. */
    var onAssistantMessage: (String) -> Unit = {}

    private var nextId = 0L

    init {
        conversations.addAll(store.loadAll())
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        add(Sender.User, clean)
        if (Config.BACKEND_URL.isBlank()) {
            add(
                Sender.System,
                "My AI brain isn't connected yet, so I can't answer. " +
                    "Your message wasn't sent anywhere. Next step: the secure backend."
            )
        }
        // Later: call the backend here and add a Sender.Cantai reply.
    }

    fun newChat() {
        messages.clear()
        nextId = 0L
        currentId = UUID.randomUUID().toString()
    }

    fun open(id: String) {
        val c = conversations.firstOrNull { it.id == id } ?: return
        messages.clear()
        messages.addAll(c.messages)
        nextId = (c.messages.maxOfOrNull { it.id } ?: -1L) + 1L
        currentId = c.id
    }

    fun delete(id: String) {
        store.delete(id)
        conversations.removeAll { it.id == id }
        if (id == currentId) newChat()
    }

    fun dismissSaveError() {
        saveFailed = false
    }

    private fun add(sender: Sender, text: String) {
        messages += ChatMessage(nextId++, sender, text)
        persist()
        if (sender != Sender.User) onAssistantMessage(text)
    }

    private fun persist() {
        val firstUser = messages.firstOrNull { it.sender == Sender.User } ?: return
        val title = firstUser.text.replace('\n', ' ').take(40)
        val convo = Conversation(currentId, title, System.currentTimeMillis(), messages.toList())
        saveFailed = !store.save(convo)
        val idx = conversations.indexOfFirst { it.id == currentId }
        if (idx >= 0) conversations.removeAt(idx)
        conversations.add(0, convo)
    }
}
