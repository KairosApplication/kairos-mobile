package com.example.kairos.viewmodel

import androidx.lifecycle.*
import com.example.kairos.model.chat.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import android.os.Handler
import android.os.Looper

data class ChatState(val loading: Boolean = false, val conversationId: String? = null,
                     val messages: List<ChatMessage> = emptyList(), val conversations: List<Conversation> = emptyList(),
                     val page: Int = 0, val hasNext: Boolean = false, val error: String = "", val draft: String = "")

class ChatViewModel(private val repository: ChatRepository = HttpChatRepository()) : ViewModel() {
    private val state = MutableLiveData(ChatState())
    val ui: LiveData<ChatState> = state
    private val worker = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var uid: String? = null
    private var generation = 0L
    private var request: Future<*>? = null

    fun account(value: String?) {
        if (uid == value) return
        generation++
        request?.cancel(true)
        uid = value
        state.value = ChatState()
    }
    fun draft(value: String) { state.value = state.value!!.copy(draft = value) }
    private fun run(action: (String, ChatState) -> ChatState) {
        val owner = uid ?: return
        val before = state.value!!
        if (before.loading) return
        val version = generation
        state.value = before.copy(loading = true, error = "")
        request = worker.submit {
            val result = try { action(owner, before) } catch (error: Exception) {
                before.copy(error = if (error is com.example.kairos.model.network.ApiFailure) error.message.orEmpty()
                    else "Não foi possível concluir. Atualize a conversa antes de reenviar: a solicitação anterior pode ter sido recebida.")
            }
            main.post { if (version == generation && uid == owner) state.value = result.copy(loading = false) }
        }
    }
    fun list(next: Boolean = false) = run { owner, before ->
        val page = if (next) before.page + 1 else 0
        val result = repository.conversations(owner, page)
        before.copy(conversations = if (next) (before.conversations + result.items).distinctBy { it.id } else result.items,
            page = page, hasNext = result.hasNext, error = "")
    }
    fun create() = run { owner, before -> before.copy(conversationId = repository.create(owner), messages = emptyList(), error = "") }
    fun open(id: String) = run { owner, before -> before.copy(conversationId = id, messages = repository.messages(owner, id), error = "") }
    fun send() = run { owner, before ->
        val question = before.draft.trim()
        require(question.isNotBlank() && question.length <= 10000)
        val reply = repository.send(owner, before.conversationId, question)
        before.copy(conversationId = reply.conversationId,
            messages = before.messages + ChatMessage("USER", question) + reply.message, draft = "", error = "")
    }
    override fun onCleared() { generation++; worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
