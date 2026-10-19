package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Explains what a button does when it's long-pressed, as a Material plain tooltip. Pass layout modifiers
 * (alignment, padding) here rather than to the button, so the button keeps its place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // The outer Box carries the layout modifiers, so alignment inside a parent Box (which only applies to
    // direct children) still works.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(text) } },
            state = rememberTooltipState(),
            content = content,
        )
    }
}

/** The back arrow for page top bars, with its tooltip. */
@Composable
fun BackButton(onBack: () -> Unit) {
    Tip("Back") {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
    }
}
