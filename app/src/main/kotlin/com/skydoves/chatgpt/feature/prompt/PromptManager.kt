package com.skydoves.chatgpt.feature.prompt

class PromptManager {
  private val cards = mutableListOf<PromptCard>()
  private var nextId = 1L

  fun addCard(fileName: String, content: String, description: String = "") {
    val card = PromptCard(nextId++, fileName, content, description)
    cards.add(0, card) // newest first
  }

  fun getAll(): List<PromptCard> = cards.toList()

  fun clearAll() { cards.clear() }
}