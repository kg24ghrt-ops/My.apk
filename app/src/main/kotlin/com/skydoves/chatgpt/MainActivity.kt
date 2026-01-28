package com.skydoves.chatgpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.skydoves.chatgpt.core.designsystem.composition.LocalOnFinishDispatcher
import com.skydoves.chatgpt.core.designsystem.theme.ChatGPTComposeTheme
import com.skydoves.chatgpt.ui.ChatGPTMain
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      CompositionLocalProvider(
        LocalOnFinishDispatcher provides { finish() }
      ) {
        ChatGPTComposeTheme { 
            ChatGPTMain(context = this) // pass the activity as context
        }
      }
    }
  }
}