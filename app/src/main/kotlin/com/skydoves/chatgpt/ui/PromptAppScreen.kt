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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@OptIn(ExperimentalMaterial3Api::class)
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

    // M3 Surface provides the correct base layer for tonal elevation
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B0E14)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            errorMessage?.let {
                ErrorPanel(it) { vm.clearError() }
                Spacer(modifier = Modifier.height(12.dp))
            }

            TopHeader()
            Spacer(modifier = Modifier.height(16.dp))
            M3ImportRow(vm)
            Spacer(modifier = Modifier.height(24.dp))

            if (bundledContent != null || activeTree != null || selectedContent != null) {
                M3PreviewPanel(
                    title = when {
                        bundledContent != null -> "AI Bundle"
                        activeTree != null -> "Project Tree"
                        else -> "File Preview"
                    },
                    content = bundledContent ?: activeTree ?: selectedContent ?: "",
                    onClose = { vm.closePreview() },
                    onCopy = { text ->
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DevAI", text))
                        Toast.makeText(ctx, "Copied!", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Text(
                    text = "Workspace Assets",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (files.isEmpty()) {
                    EmptyWorkspaceState()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(files, key = { it.id }) { f ->
                            M3FileCard(f, vm)
                        }
                    }
                }
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
        }
    }
}

@Composable
private fun M3FileCard(entity: PromptFileEntity, vm: PromptViewModel) {
    // M3 OutlinedCard for modern workspace feel
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color(0xFF161B22),
            contentColor = Color.White
        ),
        border = CardDefaults.outlinedCardBorder().copy(brush = null)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entity.language == "zip") Icons.Default.FolderZip else Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entity.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${entity.fileSizeBytes / 1024} KB • ${entity.language?.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // M3 Button group with consistent height
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { vm.loadFilePreview(entity) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("View", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = { vm.requestProjectTree(entity) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tree", fontSize = 11.sp)
                }
                Button(
                    onClick = { vm.prepareAIContext(entity) },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bundle", fontSize = 11.sp)
                }
                IconButton(
                    onClick = { vm.delete(entity) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF21262D))
                ) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFF85149))
                }
            }
        }
    }
}

@Composable
private fun M3PreviewPanel(title: String, content: String, onClose: () -> Unit, onCopy: (String) -> Unit) {
    val lines = remember(content) { content.lines() }
    
    // M3 ElevatedCard for the modal-like preview
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF0D1117))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color(0xFF00E5FF), style = MaterialTheme.typography.titleSmall)
                Row {
                    IconButton(onClick = { onCopy(content) }) { Icon(Icons.Default.ContentCopy, null, tint = Color.White) }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(lines) { line ->
                    Text(
                        text = line,
                        color = Color(0xFF79C0FF),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun M3ImportRow(vm: PromptViewModel) {
    var alias by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importUri(it, alias.ifBlank { null }); alias = "" }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text("Alias") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color(0xFF30363D),
                focusedLabelColor = Color(0xFF00E5FF)
            )
        )
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { launcher.launch("*/*") },
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
        ) {
            Icon(Icons.Default.FileUpload, null)
            Spacer(Modifier.width(8.dp))
            Text("Import")
        }
    }
}

@Composable
private fun TopHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Terminal, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text("DevAI Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

@Composable
private fun EmptyWorkspaceState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Source, null, modifier = Modifier.size(64.dp), tint = Color(0xFF30363D))
        Spacer(Modifier.height(16.dp))
        Text("No Files in Workspace", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorPanel(msg: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1919))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ReportProblem, null, tint = Color(0xFFF85149))
            Spacer(Modifier.width(12.dp))
            Text(msg, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Color(0xFFF85149)) }
        }
    }
}
