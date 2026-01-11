package com.claudemonitor.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.claudemonitor.data.api.ConnectionState
import com.claudemonitor.ui.components.LoadingIndicator
import com.claudemonitor.ui.components.TerminalKeyboard
import com.claudemonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    driverId: String,
    terminalId: String,
    viewModel: TerminalViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val buffer by viewModel.buffer.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var command by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    // Auto-scroll to bottom when buffer changes
    LaunchedEffect(buffer) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when (connectionState) {
                                        ConnectionState.CONNECTED -> Success
                                        ConnectionState.CONNECTING -> Warning
                                        else -> Error
                                    },
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                uiState.terminal?.name ?: terminalId.take(8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when (connectionState) {
                                    ConnectionState.CONNECTED -> "Connected"
                                    ConnectionState.CONNECTING -> "Connecting..."
                                    ConnectionState.DISCONNECTED -> "Disconnected"
                                    ConnectionState.ERROR -> "Error"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showKeyboard = !showKeyboard }) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "Toggle Keyboard",
                            tint = if (showKeyboard) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.reconnect() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingIndicator(modifier = Modifier.weight(1f))
                }
                uiState.error != null && connectionState == ConnectionState.ERROR -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                uiState.error ?: "Connection error",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.reconnect() }) {
                                Text("Reconnect")
                            }
                        }
                    }
                }
                else -> {
                    // Terminal output
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(TerminalBackground)
                            .verticalScroll(scrollState)
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Text(
                            text = buffer.ifEmpty { "Connecting to terminal...\n" },
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalForeground,
                            lineHeight = 16.sp
                        )
                    }

                    // Command input
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = command,
                                onValueChange = { command = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(12.dp),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (command.isEmpty()) {
                                        Text(
                                            "Type command...",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Send button (text only)
                            IconButton(
                                onClick = {
                                    if (command.isNotEmpty()) {
                                        viewModel.send(command)
                                        command = ""
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Send + Enter button
                            FilledIconButton(
                                onClick = {
                                    if (command.isNotEmpty()) {
                                        viewModel.send(command + "\r")
                                        command = ""
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.KeyboardReturn,
                                    contentDescription = "Send + Enter"
                                )
                            }
                        }
                    }

                    // Special keyboard
                    if (showKeyboard) {
                        TerminalKeyboard(
                            onKey = { viewModel.send(it) }
                        )
                    }
                }
            }
        }
    }
}
