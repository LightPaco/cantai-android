package com.cantai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>
)

/** Saves each chat as a small JSON file in the app's private storage. */
class ChatStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "chats").apply { mkdirs() }

    fun loadAll(): List<Conversation> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { read(it) }
            .sortedByDescending { it.updatedAt }

    fun save(c: Conversation): Boolean = try {
        val json = JSONObject().apply {
            put("id", c.id)
            put("title", c.title)
            put("updatedAt", c.updatedAt)
            put("messages", JSONArray().apply {
                c.messages.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("sender", m.sender.name)
                        put("text", m.text)
                    })
                }
            })
        }
        val tmp = File(dir, "${c.id}.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(File(dir, "${c.id}.json"))
    } catch (e: Exception) {
        false
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun read(f: File): Conversation? = try {
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("messages")
        val msgs = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            ChatMessage(
                id = m.getLong("id"),
                sender = Sender.valueOf(m.getString("sender")),
                text = m.getString("text")
            )
        }
        Conversation(o.getString("id"), o.getString("title"), o.getLong("updatedAt"), msgs)
    } catch (e: Exception) {
        null
    }
}
