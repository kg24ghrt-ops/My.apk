package com.skydoves.chatgpt.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Composable
fun PromptAppScreen() {
  val vm: PromptViewModel = viewModel()
  val ctx = LocalContext.current
  
  val files by vm.filesFlow.collectAsState()
  val errorMessage by vm.errorFlow.collectAsState()
  val selectedContent by vm.selectedFileContent.collectAsState()
  val activeTree by vm.activeProjectTree.collectAsState()
  val bundledContent by vm.aiContextBundle.collectAsState()
  val isProcessing by vm.isProcessing.collectAsState()

  Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      errorMessage?.let {
        ErrorPanelMessage(it) { vm.clearError() }
        Spacer(modifier = Modifier.height(8.dp))
      }

      TopBar()
      Spacer(modifier = Modifier.height(8.dp))
      FileImportRow(vm)
      Spacer(modifier = Modifier.height(12.dp))

      if (bundledContent != null || activeTree != null || selectedContent != null) {
        val panelTitle = when {
            bundledContent != null -> "AI Bundle (Cached)"
            activeTree != null -> "Project Tree"
            else -> "Source Preview"
        }
        
        PreviewPanel(
          title = panelTitle,
          content = bundledContent ?: activeTree ?: selectedContent ?: "",
          onClose = { vm.closePreview() },
          onCopy = { text ->
              val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = ClipData.newPlainText("DevAI Export", text)
              clipboard.setPrimaryClip(clip)
              Toast.makeText(ctx, "Ready to Paste", Toast.LENGTH_SHORT).show()
          }
        )
      } else {
        Text("Workspace Assets", color = Color(0xFFEEEEEE), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (files.isEmpty()) {
          Text("Workspace is empty. Import a project to begin.", color = Color(0xFFAAAAAA))
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(files, key = { it.id }) { f ->
              FileRow(f, vm)
            }
          }
        }
      }
    }

    if (isProcessing) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(color = Color(0xFF00BCD4), strokeWidth = 4.dp)
      }
    }
  }
}

@Composable
private fun PreviewPanel(title: String, content: String, onClose: () -> Unit, onCopy: (String) -> Unit) {
    // OPTIMIZATION: Process lines once per content change
    val lines = remember(content) { content.lines() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFF00BCD4), style = MaterialTheme.typography.titleMedium)
            Row {
                Button(onClick = { onCopy(content) }) { Text("Copy") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onClose) { Text("Close") }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        // VIRTUALIZED LIST: Handles millions of characters smoothly
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .padding(8.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = Color(0xFF98C379), // Nice "Hacker Green"
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FileRow(entity: PromptFileEntity, vm: PromptViewModel) {
  Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp)) {
    Text(entity.displayName, color = Color(0xFF00BCD4), style = MaterialTheme.typography.bodyLarge)
    Text("${entity.fileSizeBytes / 1024} KB • ${entity.language ?: "text"}", color = Color(0xFF888888), style = MaterialTheme.typography.bodySmall)
    
    Spacer(modifier = Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Button(modifier = Modifier.weight(1f), onClick = { vm.loadFilePreview(entity) }) {
        Text("View", style = MaterialTheme.typography.labelSmall)
      }
      Button(modifier = Modifier.weight(1f), onClick = { vm.requestProjectTree(entity) }) {
        Text("Tree", style = MaterialTheme.typography.labelSmall)
      }
      Button(modifier = Modifier.weight(1.2f), onClick = { vm.prepareAIContext(entity) }) {
        Text("Bundle", style = MaterialTheme.typography.labelSmall)
      }
      Button(modifier = Modifier.weight(0.8f), onClick = { vm.delete(entity) }) {
        Text("Del", style = MaterialTheme.typography.labelSmall, color = Color.Red)
      }
    }
  }
}

@Composable
private fun FileImportRow(vm: PromptViewModel) {
  var displayName by remember { mutableStateOf("") }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      vm.importUri(it, displayName.ifBlank { null })
      displayName = ""
    }
  }

  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.weight(1f).background(Color(0xFF1E1E1E)).padding(10.dp)) {
      if (displayName.isEmpty()) Text("Alias (Optional)", color = Color(0xFF555555), fontSize = 14.sp)
      BasicTextField(
        value = displayName,
        onValueChange = { displayName = it },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth()
      )
    }
    Button(onClick = { launcher.launch("*/*") }) { Text("Import") }
  }
}

@Composable
private fun ErrorPanelMessage(errorText: String, onDismiss: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFB00020)).padding(10.dp)) {
    Text("Engine Error", color = Color.White, style = MaterialTheme.typography.titleSmall)
    Text(errorText, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 3)
    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fix") }
  }
}

@Composable
private fun TopBar() {
  Text("DevAI Pro", color = Color(0xFF00BCD4), style = MaterialTheme.typography.headlineSmall)
}
