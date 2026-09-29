package io.github.rajumark.hoverfly.beacon.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.beacon.Beacon
import io.github.rajumark.hoverfly.beacon.DetectedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private val examples = listOf(
    "Kal milte hain bhai", "Enna panra da", "நான் வீட்டுக்கு போறேன்", "Nos vemos mañana",
    "मैं घर जा रहा हूँ", "Ami tomake bhalobashi", "Wir sehen uns morgen", "今日はいい天気ですね",
)

/** The whole demo: type text, see the detected language. [platform] is shown so screenshots say where they ran. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(platform: String) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            // Loading reads the ~8.5 MB model: do it off the main thread, once.
            val beacon by produceState<Beacon?>(null) { value = withContext(Dispatchers.Default) { Beacon() } }
            var text by remember { mutableStateOf(examples[0]) }

            Column(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Beacon", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Kotlin Multiplatform · $platform · io.github.rajumark:beacon:$BEACON_VERSION",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Type anything, in any language") },
                    modifier = Modifier.fillMaxWidth(),
                )

                val b = beacon
                if (b == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading the model…")
                    }
                } else {
                    val mark = TimeSource.Monotonic.markNow()
                    val candidates = remember(text, b) { b.candidates(text, limit = 3) }
                    val micros = remember(text, b) { mark.elapsedNow().inWholeMicroseconds }
                    ResultCard(candidates, micros)
                }

                Text("Try", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in examples) SuggestionChip(onClick = { text = e }, label = { Text(e) })
                }
                Text(
                    "Runs on this device. No network, no permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(candidates: List<DetectedLanguage>, micros: Long) {
    val best = candidates.firstOrNull() ?: DetectedLanguage.UNDETERMINED
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(best.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${best.label}  ·  ${best.language}  ·  ${best.script}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            for (c in candidates) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.label, Modifier.width(96.dp), fontFamily = FontFamily.Monospace)
                    LinearProgressIndicator(progress = { c.confidence }, modifier = Modifier.weight(1f))
                    Text("${(c.confidence * 100).roundToInt()}%", Modifier.width(56.dp).padding(start = 8.dp))
                }
            }
            Text(
                (if (best.isReliable) "Reliable" else "Uncertain") + "  ·  $micros µs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

const val BEACON_VERSION = "2.0.0"
