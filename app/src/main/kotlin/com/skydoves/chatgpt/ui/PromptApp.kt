package com.skydoves.chatgpt.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.skydoves.chatgpt.feature.chat.ChatViewModelLocal
import com.skydoves.chatgpt.feature.prompt.PromptManager
import com.skydoves.chatgpt.feature.prompt.PromptCard
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun PromptApp() {
  val promptManager = remember { PromptManager() }
  val chatViewModel = remember { ChatViewModelLocal() }

  Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      TopBar()
      Spacer(modifier = Modifier.height(12.dp))
      FilePickerCard(promptManager)
      Spacer(modifier = Modifier.height(12.dp))
      Text("Prompt Cards", color = Color(0xFFEEEEEE), style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(8.dp))
      PromptList(promptManager, chatViewModel)
    }
  }
}

@Composable
private fun TopBar() {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("Prompt Assistant", color = Color(0xFF00BCD4), style = MaterialTheme.typography.titleLarge)
  }
}

@Composable
private fun FilePickerCard(promptManager: PromptManager) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  var description by remember { mutableStateOf("") }
  var lastFileName by remember { mutableStateOf("No file selected") }

  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val text = readTextFromUri(context, it)
      lastFileName = uri.lastPathSegment ?: "file"
      if (text.isNotEmpty()) {
        promptManager.addCard(lastFileName, text, description)
        description = ""
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E1E1E))
      .padding(12.dp)
  ) {
    Text("Select a file to create a prompt card", color = Color(0xFFEEEEEE))
    Spacer(modifier = Modifier.height(6.dp))

    // BasicTextField for description (version-safe)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF2A2A2A))
        .padding(10.dp)
    ) {
      if (description.isEmpty()) {
        Text(text = "Reason / description (optional)", color = Color(0xFFAAAAAA))
      }
      BasicTextField(
        value = description,
        onValueChange = { description = it },
        singleLine = true,
        textStyle = TextStyle(color = Color.White),
        modifier = Modifier.fillMaxWidth()
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { launcher.launch("*/*") }) { Text("Pick file") }
      Button(onClick = { promptManager.clearAll() }) { Text("Clear all") }
      // quick debug/copy last description (optional)
      Button(onClick = {
        val clip = androidx.compose.ui.platform.ClipboardManager::class // no-op placeholder
      }) { Text(" ") }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text("Last: $lastFileName", color = Color(0xFFAAAAAA))
  }
}

private fun readTextFromUri(context: Context, uri: Uri): String {
  return try {
    val input = context.contentResolver.openInputStream(uri)
    val reader = BufferedReader(InputStreamReader(input))
    val text = reader.readText()
    reader.close()
    input?.close()
    text
  } catch (e: Exception) {
    ""
  }
}

@Composable
private fun PromptList(promptManager: PromptManager, chatViewModel: ChatViewModelLocal) {
  val cards = promptManager.cards // observable mutableStateListOf in manager

  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(cards) { card ->
      PromptCardRow(card = card, onCopy = { context, c -> copyToClipboard(context, c) }, onPreview = { chatViewModel.addPair(card.content) })
    }
  }
}

@Composable
private fun PromptCardRow(card: PromptCard, onCopy: (Context, PromptCard) -> Unit, onPreview: () -> Unit) {
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E1E1E))
      .padding(10.dp)
  ) {
    Text("File: ${card.fileName}", color = Color(0xFF00BCD4))
    Spacer(modifier = Modifier.height(6.dp))
    Text("Desc: ${card.description}", color = Color(0xFFEEEEEE))
    Spacer(modifier = Modifier.height(6.dp))
    Text(card.content.take(200) + if (card.content.length > 200) "..." else "", color = Color(0xFFDDDDDD))
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { onCopy(context, card) }) { Text("Copy Prompt") }
      Button(onClick = { onPreview() }) { Text("Preview in Chat") }
    }
  }
}

private fun copyToClipboard(context: Context, card: PromptCard) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
  val clip = android.content.ClipData.newPlainText("PromptCard", buildPromptText(card))
  clipboard.setPrimaryClip(clip)
}

private fun buildPromptText(card: PromptCard): String {
  return "Filename: ${card.fileName}\nDescription: ${card.description}\n\n${card.content}"
}