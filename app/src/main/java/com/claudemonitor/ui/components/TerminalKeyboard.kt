package com.claudemonitor.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalKeyboard(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Special keys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("Esc") { onKey("\u001b") }
                KeyButton("Tab") { onKey("\t") }
                KeyButton("↑") { onKey("\u001b[A") }
                KeyButton("↓") { onKey("\u001b[B") }
                KeyButton("←") { onKey("\u001b[D") }
                KeyButton("→") { onKey("\u001b[C") }
                KeyButton("Enter") { onKey("\r") }
            }

            // Row 2: Control keys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("^C", color = MaterialTheme.colorScheme.error) { onKey("\u0003") }
                KeyButton("^D") { onKey("\u0004") }
                KeyButton("^Z") { onKey("\u001a") }
                KeyButton("^L") { onKey("\u000c") }
                KeyButton("^A") { onKey("\u0001") }
                KeyButton("^E") { onKey("\u0005") }
                KeyButton("^U") { onKey("\u0015") }
                KeyButton("^K") { onKey("\u000b") }
            }

            // Row 3: Quick commands
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickButton("y") { onKey("y\r") }
                QuickButton("n") { onKey("n\r") }
                QuickButton("ls") { onKey("ls -la\r") }
                QuickButton("pwd") { onKey("pwd\r") }
                QuickButton("clear") { onKey("clear\r") }
                QuickButton("exit") { onKey("exit\r") }
                QuickButton("/compact") { onKey("/compact\r") }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.height(40.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = color
        )
    }
}

@Composable
private fun QuickButton(
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.height(40.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp
        )
    }
}
