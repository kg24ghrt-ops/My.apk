package com.skydoves.chatgpt.feature.chat

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class ChatViewModel(context: Context) {

    // Chat history: Pair<Prompt, Response>
    val chatHistory: SnapshotStateList<Pair<String, String>> = mutableStateListOf()

    /**
     * Sends a prompt to AI via custom browser tab (stub for now)
     */
    fun sendPrompt(prompt: String) {
        if (prompt.isEmpty()) return

        // Add prompt locally for UI display
        chatHistory.add(prompt to "Waiting for AI response...")

        // TODO: Implement actual custom browser tab logic
        // For now, simulate an AI response:
        val fakeResponse = "AI response for: $prompt"
        chatHistory.add(prompt to fakeResponse)
    }
}