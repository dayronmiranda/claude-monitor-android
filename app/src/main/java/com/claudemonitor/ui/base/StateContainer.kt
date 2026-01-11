package com.claudemonitor.ui.base

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.claudemonitor.core.error.AppError
import com.claudemonitor.ui.components.ErrorView

/**
 * Container that handles Loading, Error, Empty, and Content states.
 */
@Composable
fun <T> StateContainer(
    state: ContentState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    emptyIcon: ImageVector = Icons.Default.Inbox,
    emptyTitle: String = "Nothing here",
    emptyMessage: String = "No items to display",
    loadingContent: @Composable () -> Unit = { DefaultLoadingContent() },
    emptyContent: @Composable () -> Unit = {
        DefaultEmptyContent(
            icon = emptyIcon,
            title = emptyTitle,
            message = emptyMessage,
            onAction = onRetry
        )
    },
    errorContent: @Composable (AppError) -> Unit = { error ->
        ErrorView(
            error = error,
            onRetry = onRetry
        )
    },
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "state_container"
    ) { currentState ->
        when (currentState) {
            is ContentState.Loading -> {
                loadingContent()
            }
            is ContentState.Empty -> {
                emptyContent()
            }
            is ContentState.Error -> {
                errorContent(currentState.error)
            }
            is ContentState.Success -> {
                content(currentState.data)
            }
        }
    }
}

/**
 * Simplified StateContainer for lists.
 */
@Composable
fun <T> ListStateContainer(
    items: List<T>,
    isLoading: Boolean,
    error: AppError?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    emptyIcon: ImageVector = Icons.Default.Inbox,
    emptyTitle: String = "Nothing here",
    emptyMessage: String = "No items to display",
    content: @Composable (List<T>) -> Unit
) {
    val state = remember(items, isLoading, error) {
        when {
            error != null -> ContentState.Error(error)
            isLoading && items.isEmpty() -> ContentState.Loading
            items.isEmpty() -> ContentState.Empty
            else -> ContentState.Success(items)
        }
    }

    StateContainer(
        state = state,
        modifier = modifier,
        onRetry = onRetry,
        emptyIcon = emptyIcon,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        content = content
    )
}

@Composable
fun DefaultLoadingContent(
    modifier: Modifier = Modifier,
    message: String = "Loading..."
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DefaultEmptyContent(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Inbox,
    title: String = "Nothing here",
    message: String = "No items to display",
    actionLabel: String = "Refresh",
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (onAction != null) {
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(onClick = onAction) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * Overlay loading indicator.
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}
