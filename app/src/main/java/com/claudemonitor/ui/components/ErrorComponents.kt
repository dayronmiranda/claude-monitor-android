package com.claudemonitor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.claudemonitor.core.error.AppError
import com.claudemonitor.core.error.ErrorAction
import com.claudemonitor.core.error.ErrorEvent
import com.claudemonitor.core.error.ErrorHandler
import kotlinx.coroutines.flow.collectLatest

/**
 * Observes global errors and shows a Snackbar
 */
@Composable
fun GlobalErrorObserver(
    errorHandler: ErrorHandler,
    snackbarHostState: SnackbarHostState,
    onAuthError: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        errorHandler.errors.collectLatest { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.error.toUserMessage(),
                actionLabel = if (event.error.isRecoverable()) "Retry" else "Dismiss",
                duration = SnackbarDuration.Short
            )

            if (result == SnackbarResult.ActionPerformed && event.error is AppError.Auth) {
                onAuthError()
            }
        }
    }

    // Handle fatal errors (like session expiry)
    LaunchedEffect(Unit) {
        errorHandler.fatalErrors.collectLatest { error ->
            if (error is AppError.Auth) {
                onAuthError()
            }
        }
    }
}

/**
 * Full-screen error view with retry option
 */
@Composable
fun ErrorView(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null
) {
    val (icon, iconTint) = getErrorIcon(error)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = iconTint
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = getErrorTitle(error),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error.toUserMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Primary action (Retry)
        if (error.isRecoverable() && onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }

        // Secondary action
        if (onSecondaryAction != null && secondaryActionLabel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSecondaryAction) {
                Text(secondaryActionLabel)
            }
        }
    }
}

/**
 * Compact inline error message
 */
@Composable
fun InlineError(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = error.toUserMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            if (error.isRecoverable() && onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * Error banner at top of screen
 */
@Composable
fun ErrorBanner(
    error: AppError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = error.toUserMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            if (error.isRecoverable() && onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelMedium)
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Dialog for errors that need user acknowledgment
 */
@Composable
fun ErrorDialog(
    error: AppError,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null
) {
    val action = error.getSuggestedAction()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val (icon, tint) = getErrorIcon(error)
            Icon(icon, contentDescription = null, tint = tint)
        },
        title = {
            Text(getErrorTitle(error))
        },
        text = {
            Text(error.toUserMessage())
        },
        confirmButton = {
            when (action) {
                ErrorAction.Retry -> {
                    if (onRetry != null) {
                        TextButton(onClick = {
                            onDismiss()
                            onRetry()
                        }) {
                            Text("Try Again")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                }
                ErrorAction.ReAuthenticate -> {
                    TextButton(onClick = {
                        onDismiss()
                        onAction?.invoke()
                    }) {
                        Text("Sign In")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            }
        },
        dismissButton = {
            if (action == ErrorAction.Retry && onRetry != null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

/**
 * Returns appropriate icon for error type
 */
private fun getErrorIcon(error: AppError): Pair<ImageVector, Color> {
    return when (error) {
        is AppError.Network -> Icons.Default.WifiOff to Color(0xFFFF9800)
        is AppError.Auth -> Icons.Default.Lock to Color(0xFFF44336)
        is AppError.Server -> Icons.Default.CloudOff to Color(0xFFF44336)
        is AppError.Api -> Icons.Default.Error to Color(0xFFF44336)
        is AppError.WebSocket -> Icons.Default.SyncDisabled to Color(0xFFFF9800)
        is AppError.Database -> Icons.Default.Storage to Color(0xFFF44336)
        is AppError.NotFound -> Icons.Default.SearchOff to Color(0xFF9E9E9E)
        is AppError.Validation -> Icons.Default.ErrorOutline to Color(0xFFFF9800)
        is AppError.Unknown -> Icons.Default.Error to Color(0xFFF44336)
    }
}

/**
 * Returns a title for the error type
 */
private fun getErrorTitle(error: AppError): String {
    return when (error) {
        is AppError.Network -> "Connection Problem"
        is AppError.Auth -> "Authentication Required"
        is AppError.Server -> "Server Error"
        is AppError.Api -> "Request Failed"
        is AppError.WebSocket -> "Connection Lost"
        is AppError.Database -> "Storage Error"
        is AppError.NotFound -> "Not Found"
        is AppError.Validation -> "Invalid Input"
        is AppError.Unknown -> "Something Went Wrong"
    }
}
