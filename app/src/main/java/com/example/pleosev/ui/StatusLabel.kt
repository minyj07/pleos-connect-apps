package com.example.pleosev.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Connection status as outlined text. Not a button: it must not look like a (too small) touch target. */
@Composable
fun StatusLabel(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
