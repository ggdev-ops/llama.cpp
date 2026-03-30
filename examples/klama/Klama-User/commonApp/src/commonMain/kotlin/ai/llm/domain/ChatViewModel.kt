package ai.llm.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.llm.engine.EngineBridge
import ai.llm.engine.EngineSamplingParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val engineBridge: EngineBridge
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    var samplingParams = EngineSamplingParams()
        private set

    fun updateSamplingParams(params: EngineSamplingParams) {
        samplingParams = params
    }

    fun setSystemPrompt(prompt: String) {
        viewModelScope.launch {
            try {
                engineBridge.setSystemPrompt(prompt)
            } catch (e: Exception) {
                _chatState.value = _chatState.value.copy(error = e.message)
            }
        }
    }

    fun sendMessage(userMessage: String) {
        if (_chatState.value.isGenerating || userMessage.isBlank()) return

        val newMessages = _chatState.value.messages.toMutableList()
        newMessages.add(ChatMessage("user", userMessage))
        
        // Add an empty assistant message to append tokens to
        val assistantMessageIndex = newMessages.size
        newMessages.add(ChatMessage("assistant", ""))

        _chatState.value = _chatState.value.copy(
            messages = newMessages,
            isGenerating = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val flow = engineBridge.sendUserPrompt(
                    message = userMessage,
                    predictLength = 1024,
                    samplingParams = samplingParams
                )

                flow.catch { e ->
                    _chatState.value = _chatState.value.copy(
                        isGenerating = false,
                        error = e.message ?: "Error generating response"
                    )
                }.onCompletion {
                    _chatState.value = _chatState.value.copy(isGenerating = false)
                }.collect { token ->
                    val currentMessages = _chatState.value.messages.toMutableList()
                    val currentAssistantMessage = currentMessages[assistantMessageIndex]
                    currentMessages[assistantMessageIndex] = currentAssistantMessage.copy(
                        content = currentAssistantMessage.content + token
                    )
                    _chatState.value = _chatState.value.copy(messages = currentMessages)
                }
            } catch (e: Exception) {
                _chatState.value = _chatState.value.copy(
                    isGenerating = false,
                    error = e.message ?: "Failed to send message"
                )
            }
        }
    }

    fun clearHistory() {
        _chatState.value = ChatState()
        // Note: engineBridge might also need history cleared if supported, or we just reset system prompt
    }
}
