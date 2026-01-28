package com.skydoves.chatgpt.feature.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.chatgpt.BrowserAIManager
import kotlinx.coroutines.launch

class ChatViewModel(private val context: Context) : ViewModel() {

    // You can keep chat history if you want to display locally
    val chatHistory = mutableListOf<Pair<String, String>>() // Pair<Prompt, Response>

    /**
     * Sends a prompt to AI via BrowserAIManager instead of API.
     */
    fun sendPrompt(prompt: String) {
        if (prompt.isEmpty()) return

        // Add prompt locally for UI display
        chatHistory.add(Pair(prompt, "Waiting for AI response in browser tab..."))

        // Launch the browser tab
        viewModelScope.launch {
            BrowserAIManager(context)
                .openAIChat("https://chat.openai.com/") // Replace with Gemini URL if needed
        }
    }
}