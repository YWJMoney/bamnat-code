package com.bamnat.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.sleep.NoiseService
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.sleep.Noise
import com.bamnat.core.sleep.NoiseMix
import com.bamnat.core.sleep.NoiseTimer

/**
 * 1.38 [SLEEP-04] 잠드는 소리 on the sleep screen: on and off in one tap with the saved mix, and a mix of six sounds
 * with a timer under 섞기. Changes while playing are heard at once and kept for next time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SleepSoundCard(savedMix: String, savedMinutes: Int, onSave: (NoiseMix, Int) -> Unit) {
    val context = LocalContext.current
    val playing by NoiseService.playing.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var mix by remember(savedMix) { mutableStateOf(NoiseMix.decode(savedMix.ifBlank { null })) }
    var minutes by remember(savedMinutes) { mutableStateOf(savedMinutes) }
    fun changed(m: NoiseMix = mix, t: Int = minutes) {
        mix = m; minutes = t
        onSave(m, t)
        if (playing != null) NoiseService.start(context, m, t)
    }
    val names = mix.levels.filterValues { it > 0f }.keys.sortedBy { it.ordinal }.joinToString(" + ") { it.label }.ifBlank { "소리 없음" }
    SectionCard(title = "잠드는 소리") {
        Text("$names · ${NoiseTimer.label(minutes)}", style = MaterialTheme.typography.bodyMedium)
        Text("휴대폰이 바로 만들어 내는 소리라 파일이 없고, 미디어 소리 크기를 따라요(알람 크기와 따로).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (playing == null) Button(onClick = { NoiseService.start(context, mix, minutes) }, enabled = mix.levels.values.any { it > 0f }) { Text("켜기") }
            else OutlinedButton(onClick = { NoiseService.stop(context) }) { Text("끄기") }
            TextButton(onClick = { open = !open }) { Text(if (open) "섞기 접기" else "섞기·타이머") }
        }
        if (open) {
            for (n in Noise.entries) {
                val level = mix.levels[n] ?: 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                    Slider(value = level, onValueChange = { v -> mix = NoiseMix(mix.levels + (n to v)) },
                        onValueChangeFinished = { changed() },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "${n.label} 크기" })
                }
            }
            Text("타이머", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NoiseTimer.CHOICES_MINUTES.forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { changed(t = m) }, label = { Text(NoiseTimer.label(m)) })
                }
            }
            Text("타이머가 끝나기 90초 전부터 서서히 작아져요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
