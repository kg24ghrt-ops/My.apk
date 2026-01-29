package com.skydoves.chatgpt.feature.prompt

data class PromptCard(
  val id: Long,
  val fileName: String,
  val content: String,
  val description: String = ""
)