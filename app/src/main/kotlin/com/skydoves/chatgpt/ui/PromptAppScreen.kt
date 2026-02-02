package com.skydoves.chatgpt.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

// --- Design Tokens ---
private val DarkBg = Color(0xFF080A0C)
private val CardBg = Color(0xFF12161B)
private val AccentCyan = Color(0xFF00E5FF)
private val AccentPurple = Color(0xFF7000FF)
private val BorderColor = Color(0xFF232931)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptAppScreen() {
    val vm: PromptViewModel = viewModel()
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val files by vm.filesFlow.collectAsState(initial = emptyList())
    val searchQuery by vm.searchQuery.collectAsState()
    val errorMessage by vm.errorFlow.collectAsState()
    val selectedContent by vm.selectedFileContent.collectAsState()
    val activeTree by vm.activeProjectTree.collectAsState()
    val bundledContent by vm.aiContextBundle.collectAsState()
    val isProcessing by vm.isProcessing.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // Subtle Background Glow
        Box(modifier = Modifier.size(300.dp).align(Alignment.TopEnd).offset(x = 100.dp, y = (-100).dp)
            .background(Brush.radialGradient(listOf(AccentCyan.copy(alpha = 0.15f), Color.Transparent))))

        Column(modifier = Modifier.fillMaxSize()) {
            ModernTopHeader()

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { ErrorPanel(it) { vm.clearError() } }
                }

                Surface(
                    color = CardBg.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        M3SearchBar(searchQuery) { vm.updateSearchQuery(it) }
                        Spacer(Modifier.height(16.dp))
                        BundleConfigPanel(vm)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                M3ImportRow(vm)
                Spacer(modifier = Modifier.height(20.dp))

                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = (bundledContent ?: activeTree ?: selectedContent),
                        transitionSpec = { fadeIn() + scaleIn(initialScale = 0.98f) togetherWith fadeOut() },
                        label = "content_transition"
                    ) { previewContent ->
                        if (previewContent != null) {
                            IDEPreviewPanel(
                                title = when {
                                    bundledContent != null -> "AI_BUNDLE.md"
                                    activeTree != null -> "PROJECT_TREE.log"
                                    else -> "SOURCE_VIEW"
                                },
                                content = previewContent,
                                onClose = { vm.closePreview() },
                                onCopy = { text ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("DevAI", text))
                                    Toast.makeText(ctx, "Buffer Synchronized", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            WorkspaceGrid(files, searchQuery, vm)
                        }
                    }
                }
            }
        }

        if (isProcessing) {
            ProcessingOverlay()
        }
    }
}

@Composable
private fun ModernTopHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Brush.linearGradient(listOf(AccentCyan, AccentPurple)), RoundedCornerShape(14.dp))
                .padding(1.dp)
                .background(DarkBg, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Terminal, null, tint = AccentCyan, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "DEV_AI",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
            )
            Text(
                "SYSTEM ORCHESTRATOR v2.4",
                style = MaterialTheme.typography.labelSmall,
                color = AccentCyan.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun WorkspaceGrid(files: List<PromptFileEntity>, query: String, vm: PromptViewModel) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp, 18.dp).background(AccentPurple, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (query.isEmpty()) "ACTIVE_WORKSPACE" else "FILTERED_NODES",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(16.dp))

        if (files.isEmpty()) {
            EmptyWorkspaceState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(files, key = { it.id }) { f ->
                    GlassFileCard(f, vm)
                }
            }
        }
    }
}

@Composable
private fun GlassFileCard(entity: PromptFileEntity, vm: PromptViewModel) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "")

    Surface(
        onClick = { /* Detail logic */ },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(AccentCyan.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (entity.language == "zip") Icons.Rounded.FolderZip else Icons.Rounded.DataArray,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entity.displayName,
                        style = TextStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    )
                    Text(
                        "${entity.language?.uppercase() ?: "BLOB"} // ${entity.fileSizeBytes / 1024} KB",
                        style = TextStyle(fontFamily = FontFamily.Monospace, color = TextSecondary, fontSize = 11.sp)
                    )
                }

                IconButton(onClick = { vm.delete(entity) }) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = Color(0xFFFF5555), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { vm.loadFilePreview(entity) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("INSPECT", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                }

                Button(
                    onClick = { vm.prepareAIContext(entity) },
                    modifier = Modifier.weight(1.2f).shadow(elevation = 8.dp, ambientColor = AccentCyan, spotColor = AccentCyan),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = DarkBg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SYNTHESIZE", color = DarkBg, style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun IDEPreviewPanel(title: String, content: String, onClose: () -> Unit, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF0D1117))
            .border(1.dp, BorderColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Description, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onCopy(content) }) {
                Icon(Icons.Rounded.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val lines = content.lines()
                items(lines.size) { index ->
                    Row {
                        Text(
                            text = (index + 1).toString().padStart(3, ' '),
                            color = Color.DarkGray,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.width(30.dp).padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        val line = lines[index]
                        Text(
                            text = line,
                            color = when {
                                line.trim().startsWith("###") -> AccentCyan
                                line.contains(":") -> Color(0xFFD2A8FF)
                                else -> Color(0xFFC9D1D9)
                            },
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
                        )
                    }
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
            placeholder = { Text("Set logic alias...", color = TextSecondary) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedBorderColor = AccentPurple,
                unfocusedBorderColor = BorderColor
            )
        )
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { launcher.launch("*/*") },
            modifier = Modifier.height(56.dp).width(56.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White)
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)), label = ""
    )

    Box(
        modifier = Modifier.fillMaxSize().background(DarkBg.copy(alpha = 0.85f)).blur(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp).graphicsLayer { rotationZ = rotation },
                    color = AccentCyan,
                    strokeWidth = 2.dp,
                    trackColor = AccentCyan.copy(alpha = 0.1f)
                )
                Icon(Icons.Rounded.Memory, null, tint = AccentCyan, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "COMPUTING_CONTEXT...",
                style = TextStyle(fontFamily = FontFamily.Monospace, color = AccentCyan, letterSpacing = 2.sp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        label = { Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
        shape = RoundedCornerShape(6.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
            selectedLabelColor = AccentCyan
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = AccentCyan,
            borderColor = BorderColor,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        )
    )
}

@Composable
private fun M3SearchBar(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search system nodes...", color = TextSecondary, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AccentCyan) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = AccentCyan,
            focusedTextColor = Color.White
        ),
        textStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    )
}

@Composable
private fun BundleConfigPanel(vm: PromptViewModel) {
    val incTree by vm.includeTree.collectAsState()
    val incPreview by vm.includePreview.collectAsState()
    val incSummary by vm.includeSummary.collectAsState()
    val incTask by vm.includeInstructions.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConfigChip("TREE", incTree) { vm.toggleTree(it) }
        ConfigChip("CODE", incPreview) { vm.togglePreview(it) }
        ConfigChip("BRIEF", incSummary) { vm.toggleSummary(it) }
        ConfigChip("TASK", incTask) { vm.toggleInstructions(it) }
    }
}

@Composable
private fun EmptyWorkspaceState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(64.dp), tint = BorderColor)
        Spacer(Modifier.height(16.dp))
        Text("NO_DATA_NODES_FOUND", color = TextSecondary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ErrorPanel(msg: String, onDismiss: () -> Unit) {
    Surface(
        color = Color(0xFF3D1919),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFF85149))
            Spacer(Modifier.width(12.dp))
            Text(msg, color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
