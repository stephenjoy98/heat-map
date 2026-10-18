package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.data.AiEngine
import io.github.stephenjoy98.heatmap.data.Prefs

/** First-launch setup: a short welcome, then the choice of how Review insights are written. */
@Composable
fun OnboardingScreen() {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var choice by rememberSaveable { mutableStateOf<AiEngine?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (page == 0) Welcome() else {
                    Text("How should insights be written?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The Review tab can summarise where you've been, how far you went and how you got around. " +
                            "Pick who writes those insights. You can change this any time in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    AiEngineChooser(choice, { choice = it })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                if (page == 1) {
                    TextButton(onClick = { page = 0 }) { Text("Back") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    enabled = page == 0 || choice != null,
                    onClick = {
                        if (page == 0) {
                            page = 1
                        } else {
                            Prefs.setAiEngine(choice ?: AiEngine.STATS)
                            Prefs.setOnboarded()
                        }
                    },
                ) { Text(if (page == 0) "Get started" else "Done") }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Spacer(Modifier.height(24.dp))
    Text("Location Ticker", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    Text(
        "Your most travelled routes, glowing on a map.",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(24.dp))
    listOf(
        "Records your position on a tick you choose, from every 15 seconds to once a day.",
        "Works out whether you walked, cycled, drove, took a car share or transit, and asks when it isn't sure.",
        "Review tab with distance, cities, new areas explored and your steps from Health Connect.",
        "Sign in with Google to back up, and add friends by ID or QR code to compare routes and explored areas.",
    ).forEach {
        Row(Modifier.padding(vertical = 6.dp)) {
            Text("•  ", color = MaterialTheme.colorScheme.primary)
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
