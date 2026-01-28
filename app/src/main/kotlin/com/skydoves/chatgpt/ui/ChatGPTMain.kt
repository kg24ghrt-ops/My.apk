package com.skydoves.chatgpt.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skydoves.chatgpt.feature.chat.ChatViewModel

@Composable
fun ChatGPTMain(context: Context) {
    val chatViewModel = remember { ChatViewModel(context) }
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // dark background
            .padding(16.dp)
    ) {
        // Chat History
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true
        ) {
            items(chatViewModel.chatHistory.reversed()) { (prompt, response) ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "You: $prompt",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFEEEEEE) // user text color
                    )
                    Text(
                        text = "AI: $response",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF00BCD4) // AI text color
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter your prompt") },
                colors = TextFieldDefaults.textFieldColors(
                    textColor = Color.White,
                    containerColor = Color(0xFF1E1E1E),
                    placeholderColor = Color(0xFFAAAAAA)
                )
            )

            Button(onClick = {
                if (inputText.isNotBlank()) {
                    chatViewModel.sendPrompt(inputText)
                    inputText = ""
                }
            }) {
                Text("Send")
            }
        }
    }
}