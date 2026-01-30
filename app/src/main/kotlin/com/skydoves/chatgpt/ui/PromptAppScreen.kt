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
    val error by vm.errorFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // 🔴 ERROR PANEL (no crash)
        error?.let {
            ErrorPanel(it) { vm.clearError() }
            Spacer(modifier = Modifier.height(8.dp))
        }

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
private fun ErrorPanel(error: ErrorReport, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFB00020))
            .padding(10.dp)
    ) {
        Text(
            error.title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(error.message, color = Color.White)

        error.stackTrace?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                it.take(1000),
                color = Color(0xFFFFCDD2),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismiss) {
            Text("Dismiss")
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
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
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
                Text("Optional display name", color = Color(0xFF888888))
            }
            BasicTextField(
                value = displayName,
                onValueChange = { displayName = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(onClick = { launcher.launch("*/*") }) {
            Text("Import")
        }
    }
}

@Composable
private fun FileRow(entity: PromptFileEntity, vm: PromptViewModel, ctx: Context) {
    val coroutine = rememberCoroutineScope()
    var preview by remember { mutableStateOf("") }
    var nextOffset by remember { mutableStateOf(0L) }
    var expanded by remember { mutableStateOf(false) }
    var projectTree by remember { mutableStateOf("") }
    var showTree by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(10.dp)
    ) {
        Text("Name: ${entity.displayName}", color = Color(0xFF00BCD4))
        Text("Size: ${entity.fileSizeBytes} bytes", color = Color(0xFFEEEEEE))
        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            Button(onClick = {
                coroutine.launch {
                    val (text, nxt) = vm.readChunk(entity, 0L, CHUNK_BYTES)
                    preview = text
                    nextOffset = nxt
                    expanded = true
                }
            }) { Text("View") }

            Button(onClick = {
                coroutine.launch {
                    projectTree = vm.generateProjectTree(entity)
                    showTree = true
                }
            }) { Text("Project Tree") }

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
            }
        }

        if (showTree && projectTree.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFF2E2E2E))
                    .padding(8.dp)
            ) {
                Text(projectTree, color = Color(0xFFAAFFAA))
            }
        }
    }
}