package com.skydoves.chatgpt.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
    val haptics = LocalHapticFeedback.current

    val files by vm.filesFlow.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val errorMessage by vm.errorFlow.collectAsState()
    val selectedContent by vm.selectedFileContent.collectAsState()
    val activeTree by vm.activeProjectTree.collectAsState()
    val bundledContent by vm.aiContextBundle.collectAsState()
    val isProcessing by vm.isProcessing.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B0E14)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Animated Error Panel
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                errorMessage?.let {
                    ErrorPanel(it) { vm.clearError() }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            TopHeader()
            Spacer(modifier = Modifier.height(16.dp))
            
            M3SearchBar(searchQuery) { vm.updateSearchQuery(it) }
            
            Spacer(modifier = Modifier.height(20.dp))
            BundleConfigPanel(vm)
            
            Spacer(modifier = Modifier.height(20.dp))
            M3ImportRow(vm)
            Spacer(modifier = Modifier.height(24.dp))

            // Main Content Area with Crossfade for smooth switching
            Box(modifier = Modifier.weight(1f)) {
                Crossfade(targetState = (bundledContent ?: activeTree ?: selectedContent), label = "view_switcher") { previewContent ->
                    if (previewContent != null) {
                        M3PreviewPanel(
                            title = when {
                                bundledContent != null -> "AI Bundle Output"
                                activeTree != null -> "Project Architecture"
                                else -> "Source Preview"
                            },
                            content = previewContent,
                            onClose = { vm.closePreview() },
                            onCopy = { text ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("DevAI", text))
                                Toast.makeText(ctx, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Column {
                            Text(
                                text = if (searchQuery.isEmpty()) "Workspace Assets" else "Filtered Results",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (files.isEmpty()) {
                                EmptyWorkspaceState()
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    // High Performance Keying
                                    items(files, key = { it.id }) { f ->
                                        M3FileCard(f, vm)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-screen processing overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Processing Context...", color = Color(0xFF00E5FF), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun M3SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        placeholder = { Text("Search by name or extension...", color = Color.Gray, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF00E5FF)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, null, tint = Color.Gray)
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            unfocusedContainerColor = Color(0xFF161B22),
            focusedContainerColor = Color(0xFF1C2128),
            focusedBorderColor = Color(0xFF00E5FF),
            unfocusedBorderColor = Color(0xFF30363D),
            cursorColor = Color(0xFF00E5FF)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BundleConfigPanel(vm: PromptViewModel) {
    val incTree by vm.includeTree.collectAsState()
    val incPreview by vm.includePreview.collectAsState()
    val incSummary by vm.includeSummary.collectAsState()
    val incTask by vm.includeInstructions.collectAsState()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("AI BUNDLE PARAMETERS", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfigChip("File Tree", incTree) { vm.toggleTree(it) }
            ConfigChip("Code Body", incPreview) { vm.togglePreview(it) }
            ConfigChip("Briefing", incSummary) { vm.toggleSummary(it) }
            ConfigChip("Instructions", incTask) { vm.toggleInstructions(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Color.Gray,
            selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.15f),
            selectedLabelColor = Color(0xFF00E5FF)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color(0xFF30363D),
            selectedBorderColor = Color(0xFF00E5FF),
            borderWidth = 1.dp
        )
    )
}

@Composable
private fun M3FileCard(entity: PromptFileEntity, vm: PromptViewModel) {
    val haptics = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF00E5FF).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (entity.language == "zip") Icons.Default.FolderZip else Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entity.displayName, 
                        style = MaterialTheme.typography.bodyMedium, 
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "${entity.fileSizeBytes / 1024} KB • ${entity.language?.uppercase() ?: "RAW"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = { 
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.delete(entity) 
                }) {
                    Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFF85149), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.loadFilePreview(entity) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Read", fontSize = 12.sp, color = Color.White)
                }
                
                Button(
                    onClick = { 
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.prepareAIContext(entity) 
                    },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI Bundle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun M3PreviewPanel(title: String, content: String, onClose: () -> Unit, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, color = Color(0xFF00E5FF), style = MaterialTheme.typography.labelLarge)
                Text("Ready to paste into ChatGPT/Claude", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
            Row {
                IconButton(onClick = { onCopy(content) }) { 
                    Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(20.dp)) 
                }
                IconButton(onClick = onClose) { 
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp)) 
                }
            }
        }
        
        Divider(color = Color(0xFF30363D))
        
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            items(content.lines()) { line ->
                Text(
                    text = line,
                    color = when {
                        line.startsWith("###") -> Color(0xFF00E5FF)
                        line.contains("[Binary") -> Color(0xFFF85149)
                        else -> Color(0xFFC9D1D9)
                    },
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
                )
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
            placeholder = { Text("Label (optional)", fontSize = 12.sp) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color(0xFF30363D),
                focusedContainerColor = Color(0xFF161B22),
                unfocusedContainerColor = Color(0xFF161B22)
            )
        )
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { launcher.launch("*/*") },
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Import")
        }
    }
}

@Composable
private fun TopHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0095FF))),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Terminal, null, tint = Color.Black, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "DevAI Context", 
            style = MaterialTheme.typography.headlineSmall, 
            fontWeight = FontWeight.ExtraBold, 
            color = Color.White,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
private fun EmptyWorkspaceState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 60.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(48.dp), tint = Color(0xFF30363D))
        Spacer(Modifier.height(16.dp))
        Text("No assets imported", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text("Import a ZIP or Folder to begin", color = Color.Gray.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ErrorPanel(msg: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1919)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFF85149), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(msg, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
