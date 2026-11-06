package com.bamnat.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.sleep.RecoveryChallenge
import com.bamnat.core.sleep.ShiftAdapt
import com.bamnat.core.sleep.SleepCauses

/** 1.38 [SLEEP-10] 근무 바뀜 적응: today's step towards a far-away shift, with how strong each line's evidence is. */
@Composable
internal fun AdaptCard(step: ShiftAdapt.Step) {
    SectionCard(title = "근무 바뀜 준비") {
        Text(step.head, style = MaterialTheme.typography.titleSmall)
        step.lines.forEach { (line, strength) ->
            Text("· $line (${strength.label})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
        Text("일정에 맞춘 참고 정보예요. 몸이 힘들면 무리하지 마세요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 1.38 [SLEEP-13] 원인 찾기: only the user's own mornings, only from 5 times, only a clear difference. */
@Composable
internal fun CausesCard(found: List<SleepCauses.Finding>) {
    if (found.isEmpty()) return
    SectionCard(title = "내 기록에서 찾은 것") {
        found.take(3).forEach { Text(it.line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        Text("아침 한 장에서 고른 것끼리만 견줘요. 원인이라고 단정하지 않아요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 1.38 [SLEEP-20] 근무 맞춤 회복 도전: days the planned sleep was kept, rings at 14 and 21. */
@Composable
internal fun ChallengeCard(progress: RecoveryChallenge.Progress?, onStart: () -> Unit, onStop: () -> Unit) {
    SectionCard(title = "회복 도전") {
        if (progress == null) {
            Text("근무마다 계획한 잠을 채운 날을 세요. 정해진 시각이 아니라 내 근무에 맞춘 잠이에요. 14일·21일에 고리를 채워요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onStart, modifier = Modifier.padding(top = 8.dp)) { Text("도전 시작") }
            return@SectionCard
        }
        val goal = progress.nextRing ?: RecoveryChallenge.RINGS.last()
        Text(progress.line, style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { (progress.days.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (progress.short.isNotEmpty()) "계획보다 적게 잔 날 ${progress.short.size}일은 세지 않았어요" else "기록이 없는 날은 세지도, 끊지도 않아요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onStop) { Text("그만하기") }
        }
    }
}

/** 1.38 [SLEEP-22] 바꾼 것 전후: one tap marks today's change; the 7 days before and after are compared once 3 nights are on each side. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun BeforeAfterCard(mark: SleepViewModel.Mark?, onMark: (String) -> Unit, onClear: () -> Unit) {
    SectionCard(title = "바꾼 것 전후 비교") {
        if (mark == null) {
            Text("오늘 바꾼 것을 눌러 두면, 그 전 7일과 뒤 7일의 코골이 시간과 아침 기분을 견줘 드려요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                BEFORE_AFTER_CHOICES.forEach { w ->
                    com.bamnat.app.ui.components.FilterChip(selected = false, onClick = { onMark(w) }, label = { Text(w) })
                }
            }
            return@SectionCard
        }
        Text("‘${mark.what.ifEmpty { "바꾼 것" }}’ ${mark.day.monthValue}월 ${mark.day.dayOfMonth}일부터", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mark.result?.line ?: "앞뒤로 ${com.bamnat.core.sleep.BeforeAfter.MIN_NIGHTS}밤 이상 기록이 쌓이면 견줘 드려요",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("지우기") }
        }
    }
}

internal val BEFORE_AFTER_CHOICES = listOf("새 베개", "옆으로 자기", "커피 줄이기", "술 줄이기", "방 어둡게", "운동 시작", "그 밖의 것")
