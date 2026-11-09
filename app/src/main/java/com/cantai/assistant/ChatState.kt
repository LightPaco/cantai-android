package com.cantai.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

class ChatState {
    val messages = mutableStateListOf<ChatMessage>()

    var link by mutableStateOf(
        if (Config.BACKEND_URL.isBlank()) Link.NotConfigured else Link.Connecting
    )
        private set

    var thinking by mutableStateOf(false)
        private set

    private var nextId = 0L

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        messages += ChatMessage(nextId++, Sender.User, clean)
        if (Config.BACKEND_URL.isBlank()) {
            messages += ChatMessage(
                nextId++,
                Sender.System,
                "My AI brain isn't connected yet, so I can't answer. " +
                    "Your message wasn't sent anywhere. Next step: the secure backend."
            )
        }
        // Milestone 4: call the backend here and add a Sender.Cantai reply.
    }

    fun clear() {
        messages.clear()
    }
}
