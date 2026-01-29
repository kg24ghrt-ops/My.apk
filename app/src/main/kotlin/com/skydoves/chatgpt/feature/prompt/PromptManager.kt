package com.skydoves.chatgpt.feature.prompt

import androidx.compose.runtime.mutableStateListOf

data class PromptCard(
  val id: Long,
  val fileName: String,
  val content: String,
  val description: String = ""
)

class PromptManager {
  private val _cards = mutableStateListOf<PromptCard>()
  val cards: List<PromptCard> get() = _cards

  private var nextId = 1L

  fun addCard(fileName: String, content: String, description: String = "") {
    val card = PromptCard(nextId++, fileName, content, description)
    _cards.add(0, card) // newest first
  }

  fun getAll(): List<PromptCard> = cards

  fun clearAll() {
    _cards.clear()
  }
}