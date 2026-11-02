package com.example.kairos.model.chat

import com.example.kairos.model.network.*
import org.json.JSONObject

data class ChatMessage(val sender: String, val content: String, val sources: List<String> = emptyList(),
                       val blocked: Boolean = false, val approved: Boolean = true)
data class Conversation(val id: String, val label: String)
data class ConversationPage(val items: List<Conversation>, val hasNext: Boolean)
data class ChatReply(val conversationId: String, val message: ChatMessage)

interface ChatRepository {
    fun conversations(uid: String, page: Int): ConversationPage
    fun create(uid: String): String
    fun messages(uid: String, id: String): List<ChatMessage>
    fun send(uid: String, id: String?, question: String): ChatReply
}

class HttpChatRepository(private val api: KairosApi = ApiDependencies.chat()) : ChatRepository {
    private fun valid(id: String): String = id.also { require(it.matches(Regex("[a-fA-F0-9]{24}"))) }
    private fun sources(row: JSONObject): List<String> = row.optJSONArray("sources")?.let { values ->
        (0 until values.length()).map { values.getString(it) }.filter { it in setOf("pdf", "database", "memory") }
    }.orEmpty()
    override fun conversations(uid: String, page: Int): ConversationPage {
        val data = JSONObject(api.request(uid, "/api/v1/me/chat/conversations?page=$page&size=20"))
        return ConversationPage(data.getJSONArray("items").objects().map {
            Conversation(valid(it.getString("id")), it.optString("updatedAt", it.getString("id")))
        }, data.getBoolean("hasNext"))
    }
    override fun create(uid: String) = valid(JSONObject(api.request(uid, "/api/v1/me/chat/conversations", "POST", JSONObject())).getString("id"))
    override fun messages(uid: String, id: String) = JSONObject(api.request(uid, "/api/v1/me/chat/conversations/${valid(id)}"))
        .getJSONArray("messages").objects().map { ChatMessage(it.getString("sender"), it.getString("content"), sources(it)) }
    override fun send(uid: String, id: String?, question: String): ChatReply {
        require(question.isNotBlank() && question.length <= 10000)
        val request = JSONObject().put("question", question)
        id?.let { request.put("conversationId", valid(it)) }
        val reply = JSONObject(api.request(uid, "/api/v1/me/chat/messages", "POST", request))
        return ChatReply(valid(reply.getString("conversationId")), ChatMessage("AI", reply.getString("answer"), sources(reply),
            reply.getBoolean("blocked"), reply.getBoolean("approved")))
    }
}
