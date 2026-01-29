package com.skydoves.chatgpt.feature.chat

import androidx.compose.runtime.mutableStateListOf

class ChatViewModelLocal {
  val messages = mutableStateListOf<Pair<String, String>>() // prompt, response-placeholder

  fun addPair(prompt: String, response: String = "(local prompt)") {
    messages.add(0, Pair(prompt, response))
  }
}