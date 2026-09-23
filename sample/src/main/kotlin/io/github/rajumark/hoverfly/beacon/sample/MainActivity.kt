package io.github.rajumark.hoverfly.beacon.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.beacon.Beacon
import io.github.rajumark.hoverfly.beacon.DetectedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { BeaconTheme { BeaconScreen() } }
    }
}

private val EXAMPLES = listOf(
    "Kal milte hain bhai", "Enna panra da", "நான் வீட்டுக்கு போறேன்", "Nos vemos mañana",
    "मैं घर जा रहा हूँ", "Ami tomake bhalobashi", "Wir sehen uns morgen", "今日はいい天気ですね",
)

/** Result of one inference, with its wall-clock time. */
private class Result(val candidates: List<DetectedLanguage>, val micros: Long)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BeaconScreen() {
    val context = LocalContext.current.applicationContext

    // Loading reads ~8.5 MB: do it once, off the main thread.
    val beacon by produceState<Beacon?>(null) {
        value = withContext(Dispatchers.Default) { Beacon(context) }
        awaitDispose { value?.close() }
    }
    var text by remember { mutableStateOf(EXAMPLES[0]) }

    val result by produceState<Result?>(null, beacon, text) {
        val b = beacon ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val t0 = System.nanoTime()
            val c = b.candidates(text, limit = 3)
            Result(c, (System.nanoTime() - t0) / 1000)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Beacon") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Type anything, in any language") },
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                },
            )

            val r = result
            when {
                beacon == null || r == null -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> ResultCard(r)
            }

            Text("Try", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EXAMPLES.forEach { SuggestionChip(onClick = { text = it }, label = { Text(it) }) }
            }
            Text(
                "Runs on this device. No network, no permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(r: Result) {
    val best = r.candidates.firstOrNull() ?: DetectedLanguage.UNDETERMINED
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
            r.candidates.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.label, Modifier.width(96.dp), fontFamily = FontFamily.Monospace)
                    LinearProgressIndicator(progress = { c.confidence }, modifier = Modifier.weight(1f))
                    Text(
                        String.format(Locale.ROOT, "%.0f%%", c.confidence * 100),
                        Modifier.width(56.dp).padding(start = 8.dp),
                    )
                }
            }
            Text(
                (if (best.isReliable) "Reliable" else "Uncertain") + "  ·  ${r.micros} µs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun BeaconTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
