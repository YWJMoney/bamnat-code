package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.Membership
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.ConfirmDialog
import com.bamnat.app.ui.components.NudgeCard
import com.bamnat.app.ui.settings.InquiryLinks
import com.bamnat.app.ui.settings.InquiryStart
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import com.bamnat.core.text.Josa
import com.bamnat.core.time.Today
import java.time.Instant

/**
 * 2.0.0 [RVN-17] 검토 COMP-12·U10a-12 (보고서 11장 결정 10: 고르기 전에 알리고, '잘못 골랐어요'로 한 번은 풀 수 있게). Why this calendar
 * cannot take a company now: the company [held] holding it (null from a server before 2.0.0), until when, and whether the server lets
 * '잘못 골랐어요' free it now ([undo], once a year).
 */
data class MemberWait(val held: String?, val until: Instant?, val undo: Boolean, val why: String? = null, val again: Instant? = null) {
    /** "이 달력은 12월 10일까지 ‘가나상사’로 정해져 있어요" */
    val line: String get() {
        val day = until?.let { Fmt.monthDay(it.atZone(Today.zone()).toLocalDate()) }
        val who = held?.takeIf { it.isNotBlank() }?.let { "‘$it’" + Josa.fit(it, "으로") }
        return when {
            who != null && day != null -> "이 달력은 ${day}까지 ${who} 정해져 있어요"
            who != null -> "이 달력은 ${who} 정해져 있어요. 정한 지 30일이 지나면 바꿀 수 있어요"
            day != null -> "이 달력은 ${day}까지 다른 회사로 정해져 있어요"
            else -> companyProblem("member_wait")
        }
    }

    /**
     * 2.0.0 [RVN-17] 검토 PC-155 [line] as the screen shows it: the company's name and its 조사 kept on one line (at 100% the title broke as
     * '…‘(주)예시정밀’ / 로 정해져 있어요'). Word joiners between the letters, as the 근무 기록 PDF keeps a word whole.
     */
    val shown: String get() {
        val h = held?.takeIf { it.isNotBlank() } ?: return line
        val who = "‘$h’" + Josa.fit(h, "으로")
        return line.replace(who, keepWhole(who))
    }

    /**
     * 2.0.0 [RVN-17] PC-155 Why '잘못 골랐어요' is not offered now: it was used (once a year) and opens again on [again]; null while it is
     * open or when the server did not say (a server before this, or the way closed).
     */
    val noUndo: String? get() = again?.takeIf { !undo }?.let { a ->
        val d = a.atZone(Today.zone()).toLocalDate()
        "잘못 고른 회사 풀기를 이미 썼어요(한 해에 한 번 · ${d.year}년 ${Fmt.monthDay(d)}부터 다시)"
    }

    /**
     * When '잘못 골랐어요' could not free it: the reason on the same card ([why], not in red: nothing is broken), and the button only while
     * trying again could help (no signal, not once a year used up or the way closed).
     */
    fun failed(e: CompanyException): MemberWait = copy(undo = undo && e.code != "undo_used" && e.code != "closed", why = undoProblem(e))

    companion object {
        fun of(m: Membership): MemberWait? = if (m.error == "member_wait") MemberWait(m.held, m.until, m.undo, again = m.undoAgain) else null
        fun of(e: CompanyException): MemberWait? =
            if (e.code == "member_wait") MemberWait(e.held, e.untilMillis?.let(Instant::ofEpochMilli), e.undo,
                again = e.undoAgain?.let { runCatching { Instant.parse(it) }.getOrNull() }) else null
    }
}

/**
 * What to say when '잘못 골랐어요' could not free the calendar, short enough for the card's second line (the first already says until
 * when it is held, so the day is not said twice).
 */
/** Letters glued so a line never breaks inside [s] (spaces still may): U+2060 between two letters. */
private fun keepWhole(s: String): String = buildString {
    s.forEachIndexed { i, c -> if (i > 0 && !c.isWhitespace() && !s[i - 1].isWhitespace()) append('\u2060'); append(c) }
}

/** 2.0.0 [RVN-17] PC-155 The second line under a held calendar when '잘못 골랐어요' is not offered: why, then 문의. */
internal fun MemberWait.askLine(): String = noUndo?.let { "$it. 꼭 바꿔야 하면 문의해 주세요" } ?: "꼭 바꿔야 할 사정이 있으면 문의해 주세요"

internal fun undoProblem(e: CompanyException): String = when (e.code) {
    "undo_used" -> "잘못 고른 회사 풀기를 이미 썼어요(한 해에 한 번)"
    "closed" -> "지금은 바로 풀 수 없어요. 그날 뒤에 바꿀 수 있어요"
    else -> companyProblem(e)
}

/**
 * The wait, said where the user is about to put [company] in (not at the end of picking an order, 보고서 재현), in the app's one guide
 * card ([NudgeCard], 조건 3: a caution icon, not red, since nothing went wrong; one text button and the X). Its button is
 * '잘못 골랐어요' while the server allows it, asked once more because it can be done once a year; otherwise 문의하기, for a reason
 * the rule does not cover (검토 I10ab-02 '실패 말에 풀리는 날짜와 문의 단추'). [busy] while it is being freed.
 */
@Composable
fun MemberWaitCard(wait: MemberWait, company: String, busy: Boolean, onUndo: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf(false) }
    val hint = wait.why ?: if (wait.undo) "잘못 골랐다면 한 번 풀고 ‘$company’${Josa.eulreul(company)} 쓸 수 있어요(한 해에 한 번)"
        else wait.askLine()
    NudgeCard(
        wait.shown, modifier = modifier, tone = Tone.Caution, detail = hint,
        action = if (busy) "푸는 중…" else if (wait.undo) "잘못 골랐어요" else "문의하기",
        onAction = {
            if (busy) Unit
            else if (wait.undo) asking = true
            else InquiryLinks.ask(InquiryStart(about = "회사 바꾸기", text = "${wait.line}. "))
        },
        onClose = onClose, closeLabel = "닫기",
    )
    if (asking) UndoDialog(wait, company, onDismiss = { asking = false }, onConfirm = { asking = false; onUndo() })
}

/** '잘못 골랐어요', asked once more: it frees the calendar at once and can be done once a year. */
@Composable
private fun UndoDialog(wait: MemberWait, company: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val held = wait.held?.takeIf { it.isNotBlank() }?.let { "‘$it’" } ?: "지금 회사"
    ConfirmDialog(
        title = "$held 참여를 풀까요?",
        message = "이 달력의 회사를 풀고 바로 ‘$company’${Josa.eulreul(company)} 이어서 넣어요. 잘못 고른 회사 풀기는 한 해에 한 번만 할 수 있어요.",
        confirmLabel = "풀고 이어서 넣기",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 2.0.0 [RVN-17] 검토 COMP-12·I10ab-02 이 설정 쓰기 on [company] while this calendar is held by another: said before any order is picked,
 * with the two ways on. 근무주기만 넣기 (filled: nothing is used up, 검토 I10ab-02 '근무주기만 넣기는 참여 없이') puts the order in and
 * leaves the company as it is; '잘못 골랐어요' (asked once more) frees the calendar and goes on as a member, or 문의하기 when it cannot.
 */
@Composable
fun HeldDialog(wait: MemberWait, company: String, onCycleOnly: () -> Unit, onUndo: () -> Unit, onDismiss: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val held = wait.held?.takeIf { it.isNotBlank() }?.let { "‘$it’" } ?: "지금 회사"
    if (asking) return UndoDialog(wait, company, onDismiss = { asking = false }, onConfirm = { asking = false; onUndo() })
    AlertDialog(
        onDismissRequest = onDismiss,
        // PC-155 The caution mark the guide card has (nothing went wrong, so not red), and the name kept with its 조사.
        icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.WarningAmber, contentDescription = "주의",
            tint = Tone.Caution.color()) },
        title = { Text(wait.shown) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 총괄 사진 검토: one word for it, 근무주기, as the button says.
                Text("‘$company’ 근무주기는 지금 넣을 수 있어요. 회사와 급여 방식은 $held 그대로예요.", style = MaterialTheme.typography.bodyMedium)
                Text(if (wait.undo) "$held${Josa.fit(held.trim('‘', '’'), "을")} 잘못 골랐다면 한 번 풀고 ‘$company’ 설정을 그대로 쓸 수 있어요(한 해에 한 번)."
                    else wait.askLine() + ".",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onCycleOnly) { Text("근무주기만 넣기") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("닫기") }
                if (wait.undo) TextButton(onClick = { asking = true }) { Text("잘못 골랐어요") }
                else TextButton(onClick = { onDismiss(); InquiryLinks.ask(InquiryStart(about = "회사 바꾸기", text = "${wait.line}. ")) }) { Text("문의하기") }
            }
        },
    )
}

/**
 * 2.0.0 [RVN-17] 총괄 사진 검토 (rvn_95·97): on another company's page of a held calendar, what holds it and what can be done here, with
 * the way out beside it: 잘못 골랐어요 (asked once more, [UndoDialog]) while this year's is unused, else why not and 문의하기. The page's use
 * buttons then say 근무주기만 넣기 and do just that ([HELD_USE]).
 */
@Composable
fun HeldNotice(wait: MemberWait, company: String, payHidden: Boolean, freeing: Boolean, onUndo: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    if (asking) UndoDialog(wait, company, onDismiss = { asking = false }, onConfirm = { asking = false; onUndo() })
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(wait.shown + " · 근무주기만 넣을 수 있어요." + (if (payHidden) " 급여 계산 방식은 이 회사 설정을 쓰는(참여한) 분께만 보여요." else ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!wait.undo) Text(wait.askLine() + ".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            if (wait.undo) TextButton(onClick = { asking = true }, enabled = !freeing) { Text(if (freeing) "푸는 중…" else "잘못 골랐어요") }
            else TextButton(onClick = { InquiryLinks.ask(InquiryStart(about = "회사 바꾸기", text = "${wait.line}. ")) }) { Text("문의하기") }
        }
    }
}
