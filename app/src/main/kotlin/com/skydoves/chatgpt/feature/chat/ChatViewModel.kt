package com.skydoves.chatgpt.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.skydoves.chatgpt.BrowserAIManager

class ChatViewModel(private val context: Context) : ViewModel() {

    // observable chat history for Compose
    val chatHistory = mutableStateListOf<Pair<String, String>>() // Pair<prompt, response>

    /**
     * Copies prompt to clipboard and opens AI web UI in custom tab.
     * User must paste+send in the browser.
     */
    fun sendPromptViaBrowser(prompt: String) {
        if (prompt.isBlank()) return

        // add a placeholder entry to UI
        chatHistory.add(prompt to "Waiting for AI response... (import from clipboard)")

        // copy prompt to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Prompt", prompt)
        clipboard.setPrimaryClip(clip)

        // open AI web UI in custom tab (user will paste & send)
        BrowserAIManager(context).openAIChat("https://chat.openai.com/") // or Gemini URL
    }

    /**
     * Reads clipboard and inserts text as the response for the last prompt.
     * Call this when the user has copied the AI reply in their browser.
     */
    fun importResponseFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return
        val item = clipboard.primaryClip?.getItemAt(0) ?: return
        val text = item.coerceToText(context).toString().trim()
        if (text.isEmpty()) return

        // update last entry's response if exists
        if (chatHistory.isNotEmpty()) {
            val lastIndex = chatHistory.lastIndex
            val (prompt, _) = chatHistory[lastIndex]
            chatHistory[lastIndex] = prompt to text
        } else {
            // no prompt present, create entry with empty prompt
            chatHistory.add("" to text)
        }
    }
}