package com.claudemonitor.ui.screens.drivers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.claudemonitor.core.error.AppError
import com.claudemonitor.data.model.DriverStatus
import com.claudemonitor.ui.components.ErrorBanner
import com.claudemonitor.ui.components.ErrorDialog
import com.claudemonitor.ui.theme.Success
import com.claudemonitor.ui.theme.Error
import com.claudemonitor.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversScreen(
    viewModel: DriversViewModel = hiltViewModel(),
    onDriverClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude Monitor") },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Driver")
            }
        }
    ) { padding ->
        if (uiState.drivers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No drivers configured",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add a driver to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.drivers, key = { it.driver.id }) { driverWithStatus ->
                    DriverItem(
                        driverWithStatus = driverWithStatus,
                        onClick = { onDriverClick(driverWithStatus.driver.id) },
                        onDelete = { viewModel.deleteDriver(driverWithStatus.driver.id) }
                    )
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        AddDriverDialog(
            isLoading = uiState.isAddingDriver,
            error = uiState.error,
            onDismiss = { viewModel.hideAddDialog() },
            onAdd = { name, url, username, password ->
                viewModel.addDriver(name, url, username, password)
            },
            onClearError = { viewModel.clearError() }
        )
    }

    // Show error dialog for non-validation errors
    uiState.error?.let { error ->
        if (error !is AppError.Validation && !uiState.showAddDialog) {
            ErrorDialog(
                error = error,
                onDismiss = { viewModel.clearError() },
                onRetry = if (error.isRecoverable()) {
                    { viewModel.refreshStatus() }
                } else null
            )
        }
    }
}

@Composable
fun DriverItem(
    driverWithStatus: DriverWithStatus,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                    .padding(end = 0.dp)
            ) {
                val color = when (driverWithStatus.status) {
                    DriverStatus.ONLINE -> Success
                    DriverStatus.OFFLINE -> Error
                    DriverStatus.CONNECTING -> Warning
                    DriverStatus.ERROR -> Error
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.small,
                    color = color
                ) {}
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driverWithStatus.driver.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = driverWithStatus.driver.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Driver") },
            text = { Text("Are you sure you want to delete ${driverWithStatus.driver.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddDriverDialog(
    isLoading: Boolean = false,
    error: AppError? = null,
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, username: String, password: String) -> Unit,
    onClearError: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("http://") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Get field-specific error if it's a validation error
    val validationError = error as? AppError.Validation
    val nameError = validationError?.takeIf { it.field == "name" }
    val urlError = validationError?.takeIf { it.field == "url" }
    val passwordError = validationError?.takeIf { it.field == "password" }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Driver") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Show general error (non-validation)
                if (error != null && error !is AppError.Validation) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                error.toUserMessage(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; if (nameError != null) onClearError() },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it.message) } },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; if (urlError != null) onClearError() },
                    label = { Text("URL") },
                    singleLine = true,
                    placeholder = { Text("http://192.168.1.100:9090") },
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it.message) } },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; if (passwordError != null) onClearError() },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it.message) } },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    enabled = !isLoading,
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url, username, password) },
                enabled = name.isNotBlank() && url.isNotBlank() && password.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}
