package com.skydoves.chatgpt.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Composable
fun PromptAppScreen() {
  val vm: PromptViewModel = viewModel()
  val ctx = LocalContext.current
  
  // State collection from ViewModel flows
  val files by vm.filesFlow.collectAsState()
  val errorMessage by vm.errorFlow.collectAsState()
  val selectedContent by vm.selectedFileContent.collectAsState()
  val activeTree by vm.activeProjectTree.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF121212))
      .padding(12.dp)
  ) {
    // Error Reporting Panel
    errorMessage?.let {
      ErrorPanelMessage(it) { vm.clearError() }
      Spacer(modifier = Modifier.height(8.dp))
    }

    TopBar()
    Spacer(modifier = Modifier.height(8.dp))
    FileImportRow(vm)
    Spacer(modifier = Modifier.height(12.dp))

    // Preview/Tree Display Overlay
    if (selectedContent != null || activeTree != null) {
      PreviewPanel(
        title = if (activeTree != null) "Project Tree" else "File Preview",
        content = activeTree ?: selectedContent ?: "",
        onClose = { vm.closePreview() },
        onCopy = { text ->
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("DevAI Export", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
      )
    } else {
      // Main Workspace: File List
      Text(
        "Imported Workspace Files",
        color = Color(0xFFEEEEEE),
        style = MaterialTheme.typography.titleMedium
      )
      Spacer(modifier = Modifier.height(8.dp))

      if (files.isEmpty()) {
        Text("No files imported yet. Start by importing a project folder or file.", color = Color(0xFFAAAAAA))
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(files) { f ->
            FileRow(f, vm)
          }
        }
      }
    }
  }
}

@Composable
private fun PreviewPanel(title: String, content: String, onClose: () -> Unit, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color(0xFF00BCD4), style = MaterialTheme.typography.titleMedium)
            Row {
                Button(onClick = { onCopy(content) }) { Text("Copy") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onClose) { Text("Close") }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(content, color = Color(0xFFAAFFAA), style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
        }
    }
}

@Composable
private fun FileRow(entity: PromptFileEntity, vm: PromptViewModel) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E1E1E))
      .padding(12.dp)
  ) {
    Text(entity.displayName, color = Color(0xFF00BCD4), style = MaterialTheme.typography.bodyLarge)
    Text("${entity.fileSizeBytes} bytes", color = Color(0xFF888888), style = MaterialTheme.typography.bodySmall)
    
    Spacer(modifier = Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(modifier = Modifier.weight(1f), onClick = { vm.loadFilePreview(entity) }) {
        Text("Preview")
      }
      Button(modifier = Modifier.weight(1f), onClick = { vm.requestProjectTree(entity) }) {
        Text("Tree")
      }
      Button(modifier = Modifier.weight(0.7f), onClick = { vm.delete(entity) }) {
        Text("Delete")
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
      if (displayName.isEmpty()) Text("Alias (Optional)", color = Color(0xFF555555))
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
private fun ErrorPanelMessage(errorText: String, onDismiss: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFFB00020))
      .padding(10.dp)
  ) {
    Text("Runtime Error", color = Color.White, style = MaterialTheme.typography.titleSmall)
    Text(errorText, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 5)
    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Dismiss") }
  }
}

@Composable
private fun TopBar() {
  Text("DevAI Assistant", color = Color(0xFF00BCD4), style = MaterialTheme.typography.headlineSmall)
}
