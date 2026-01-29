package com.skydoves.chatgpt.ui

import android.content.Context
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.skydoves.chatgpt.feature.chat.ChatViewModel

@Composable
fun ChatGPTMain(context: Context) {
    val chatViewModel = remember { ChatViewModel(context) }
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Chat history
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true
        ) {
            items(chatViewModel.chatHistory.reversed()) { (prompt, response) ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "You: $prompt",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFEEEEEE)
                    )
                    Text(
                        text = "AI: $response",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF00BCD4)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input row and actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Input box (BasicTextField version for compatibility)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                val textStyle = TextStyle(color = Color.White)
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    textStyle = textStyle,
                    modifier = Modifier.fillMaxWidth()
                ) { inner ->
                    if (inputText.isEmpty()) {
                        Text("Enter your prompt", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodyMedium)
                    }
                    inner()
                }
            }

            // Send: copies prompt to clipboard and opens the AI page for user to paste/send
            Button(onClick = {
                if (inputText.isNotBlank()) {
                    chatViewModel.sendPromptViaBrowser(inputText)
                    inputText = ""
                }
            }) {
                Text("Send")
            }

            // Import: read clipboard and treat as AI response (user must copy from browser first)
            Button(onClick = {
                chatViewModel.importResponseFromClipboard()
            }) {
                Text("Import")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Workflow: Send → paste+send in browser → copy response → Import",
            color = Color(0xFF888888),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}