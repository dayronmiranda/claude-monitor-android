package com.claudemonitor.ui.screens.terminals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.claudemonitor.data.model.Terminal
import com.claudemonitor.ui.components.LoadingIndicator
import com.claudemonitor.ui.components.ErrorMessage
import com.claudemonitor.ui.theme.Success
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalsScreen(
    driverId: String,
    viewModel: TerminalsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onTerminalClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.terminalCreated.collectLatest { terminalId ->
            onTerminalClick(terminalId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminals")
                        Text(
                            "${uiState.terminals.size} active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Terminal")
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(padding))
            }
            uiState.error != null -> {
                ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadData() },
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.terminals.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No active terminals",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.terminals, key = { it.id }) { terminal ->
                        TerminalItem(
                            terminal = terminal,
                            onClick = { onTerminalClick(terminal.id) },
                            onKill = { viewModel.killTerminal(terminal.id) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showCreateDialog) {
        CreateTerminalDialog(
            isCreating = uiState.creatingTerminal,
            onDismiss = { viewModel.hideCreateDialog() },
            onCreate = { workDir, type -> viewModel.createTerminal(workDir, type) }
        )
    }
}

@Composable
fun TerminalItem(
    terminal: Terminal,
    onClick: () -> Unit,
    onKill: () -> Unit
) {
    var showKillConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.small,
                    color = if (terminal.active) Success else MaterialTheme.colorScheme.error
                ) {}
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = terminal.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = terminal.workDir,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(terminal.type, style = MaterialTheme.typography.labelSmall) }
                    )
                    if (terminal.clients > 0) {
                        Text(
                            text = "${terminal.clients} clients",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = { showKillConfirm = true }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Kill",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            title = { Text("Kill Terminal") },
            text = { Text("Are you sure you want to kill this terminal?") },
            confirmButton = {
                TextButton(onClick = {
                    onKill()
                    showKillConfirm = false
                }) {
                    Text("Kill", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTerminalDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (workDir: String, type: String) -> Unit
) {
    var workDir by remember { mutableStateOf("/root") }
    var selectedType by remember { mutableStateOf("claude") }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("Create Terminal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = workDir,
                    onValueChange = { workDir = it },
                    label = { Text("Working Directory") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating
                )

                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "claude",
                        onClick = { selectedType = "claude" },
                        label = { Text("Claude") },
                        enabled = !isCreating
                    )
                    FilterChip(
                        selected = selectedType == "shell",
                        onClick = { selectedType = "shell" },
                        label = { Text("Shell") },
                        enabled = !isCreating
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(workDir, selectedType) },
                enabled = workDir.isNotBlank() && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
}
