package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.decimalText

/**
 * 2.0.0 [RVJ-76] 검토 PAYU-49·T05b-02·I05ba-02 (일근 포괄임금): 고정 연장 시간. The hours most contracts say are one tap; '직접' takes what a
 * contract really says (12·22.5·45시간) on the 0.5시간 grid, where the old chips (0·10·20·30·40·52) could not. One picker for the
 * three places that ask it (수당 창, 급여 마법사의 연봉, 빠른 설정의 포괄임금), so it looks and works the same in each.
 *
 * [minutes] 0 is none, named by [none] (null leaves that chip out: 빠른 설정 turns 포괄 off with its own switch).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FixedOtHours(minutes: Int, onPick: (Int) -> Unit, none: String?, modifier: Modifier = Modifier) {
    var typing by rememberSaveable { mutableStateOf(minutes > 0 && minutes !in FixedOtHoursRules.QUICK.map { it * 60 }) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (none != null) FilterChip(selected = !typing && minutes == 0, onClick = { typing = false; onPick(0) }, label = { Text(none) })
            FixedOtHoursRules.QUICK.forEach { h ->
                FilterChip(selected = !typing && minutes == h * 60, onClick = { typing = false; onPick(h * 60) }, label = { Text("${h}시간분") })
            }
            FilterChip(selected = typing, onClick = { typing = true }, label = { Text("직접") })
        }
        if (typing) FixedOtHoursField(minutes, onPick)
    }
}

/**
 * The 직접 칸: digits and one point, on the number pad. 1.36 PC X1 ('20' 칸에 20을 치니 '202'): a tap selects the whole number, so typing
 * replaces it; 03df48fb: the tap that focuses the field moves the cursor after focusing, so the select-all is put back once. A value
 * off the grid is said and not used; the hours in use stay as they were.
 */
@Composable
private fun FixedOtHoursField(minutes: Int, onPick: (Int) -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(if (minutes > 0) FixedOtHoursRules.text(minutes) else ""))
    }
    var selectNext by rememberSaveable { mutableStateOf(false) }
    val read = FixedOtHoursRules.read(text.text)
    val bad = text.text.isNotEmpty() && read == null
    OutlinedTextField(
        text,
        { v ->
            val clean = FixedOtHoursRules.clean(v.text)
            if (selectNext && clean == text.text) {
                selectNext = false
                text = text.copy(selection = TextRange(0, clean.length))
                return@OutlinedTextField
            }
            selectNext = false
            text = v.copy(text = clean, selection = if (clean == v.text) v.selection else TextRange(clean.length))
            FixedOtHoursRules.read(clean)?.let(onPick)
        },
        label = { Text("고정 연장 시간 (한 달)") },
        suffix = { Text("시간") },
        placeholder = { Text("예: 22.5") },
        singleLine = true,
        isError = bad,
        supportingText = if (bad) ({
            Text("0.5시간 단위로 200시간까지 넣어 주세요. " +
                if (minutes > 0) "지금은 ${FixedOtHoursRules.text(minutes)}시간으로 계산해요" else "아직 넣지 않았어요")
        }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().onFocusChanged { s ->
            if (s.isFocused) { selectNext = true; text = text.copy(selection = TextRange(0, text.text.length)) } else selectNext = false
        },
    )
}

/** [RVJ-76] The picker's numbers, apart so a test reads them without a screen. */
internal object FixedOtHoursRules {
    /** The hours most contracts say (주 52시간 상한의 한 달 몫까지). */
    val QUICK = listOf(10, 20, 30, 40, 52)
    const val MAX_MINUTES = 200 * 60

    /** 1,350분 → "22.5", 1,200분 → "20". */
    fun text(minutes: Int): String = decimalText(minutes / 60.0)

    /** Digits and the first point, three digits before it and one after. */
    fun clean(raw: String): String {
        val s = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
        val dot = s.indexOf('.')
        return if (dot < 0) s.take(3) else s.substring(0, dot).take(3) + "." + s.substring(dot + 1).filter(Char::isDigit).take(1)
    }

    /** The minutes [text] says on the 0.5시간 grid, 0.5 to 200시간; null when it is not one. */
    fun read(text: String): Int? {
        val v = text.toDoubleOrNull() ?: return null
        val half = v * 2
        if (half != Math.rint(half)) return null
        val minutes = (v * 60).toInt()
        return minutes.takeIf { it in 30..MAX_MINUTES }
    }
}
