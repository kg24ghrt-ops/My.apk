@file:OptIn(androidx.lifecycle.compose.ExperimentalLifecycleComposeApi::class)

package com.skydoves.chatgpt.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import kotlinx.coroutines.launch

private const val CHUNK_BYTES = 32 * 1024 // 32 KB chunks

@Composable
fun PromptAppScreen() {
  val vm: PromptViewModel = viewModel()
  val ctx = LocalContext.current
  val files by vm.filesFlow.collectAsStateWithLifecycle(emptyList())

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF121212))
      .padding(12.dp)
  ) {
    TopBar()
    Spacer(modifier = Modifier.height(8.dp))
    FileImportRow(vm)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      "Imported Files",
      color = Color(0xFFEEEEEE),
      style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (files.isEmpty()) {
      Text("No files yet", color = Color(0xFFAAAAAA))
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(files) { f -> FileRow(f, vm, ctx) }
      }
    }
  }
}

@Composable
private fun TopBar() {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      "Prompt Assistant",
      color = Color(0xFF00BCD4),
      style = MaterialTheme.typography.titleLarge
    )
  }
}

@Composable
private fun FileImportRow(vm: PromptViewModel) {
  var displayName by remember { mutableStateOf("") }
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      vm.importUri(it, displayName.ifBlank { null })
      displayName = ""
    }
  }

  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .background(Color(0xFF1E1E1E))
        .padding(8.dp)
    ) {
      if (displayName.isEmpty()) {
        Text(text = "Optional display name", color = Color(0xFF888888))
      }
      BasicTextField(
        value = displayName,
        onValueChange = { displayName = it },
        singleLine = true,
        textStyle = TextStyle(color = Color.White),
        modifier = Modifier.fillMaxWidth()
      )
    }

    Button(onClick = { launcher.launch("*/*") }) { Text("Import") }
  }
}

@Composable
private fun FileRow(entity: PromptFileEntity, vm: PromptViewModel, ctx: Context) {
  val coroutine = rememberCoroutineScope()
  var preview by remember { mutableStateOf("") }
  var nextOffset by remember { mutableStateOf(0L) }
  var loading by remember { mutableStateOf(false) }
  var expanded by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E1E1E))
      .padding(10.dp)
  ) {
    Text("Name: ${entity.displayName}", color = Color(0xFF00BCD4))
    Spacer(modifier = Modifier.height(6.dp))
    Text("Size: ${entity.fileSizeBytes} bytes", color = Color(0xFFEEEEEE))
    Spacer(modifier = Modifier.height(6.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = {
        if (!loading) {
          loading = true
          coroutine.launch {
            val (text, nxt) = vm.readChunk(entity, 0L, CHUNK_BYTES)
            preview = text
            nextOffset = if (nxt < 0) -1L else nxt
            expanded = true
            loading = false
          }
        } else {
          expanded = !expanded
        }
      }) { Text("View") }

      Button(onClick = { copyPromptReferenceToClipboard(ctx, entity) }) { Text("Copy prompt") }

      Button(onClick = { vm.delete(entity) }) { Text("Delete") }
    }

    if (expanded) {
      Spacer(modifier = Modifier.height(8.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 120.dp, max = 400.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Text(preview, color = Color(0xFFDDDDDD))
        if (nextOffset > 0) {
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
              coroutine.launch {
                val (text, nxt) = vm.readChunk(entity, nextOffset, CHUNK_BYTES)
                preview += "\n" + text
                nextOffset = if (nxt < 0) -1L else nxt
              }
            }) { Text("Load more") }
          }
        }
      }
    }
  }
}

private fun copyPromptReferenceToClipboard(ctx: Context, entity: PromptFileEntity) {
  val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
  val text = "Filename: ${entity.displayName}\nPath: ${entity.filePath}\n\n(Use this file in your prompt; open file in app to preview content.)"
  val clip = android.content.ClipData.newPlainText("PromptRef", text)
  clipboard.setPrimaryClip(clip)
}