package com.bamnat.app.ui.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanyText
import com.bamnat.app.company.PayFind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PLAZA-16] 1.27 묶음 AH: the way into 우리 회사 급여 방식 찾기 at the top of the easy pay setup (main calendar, company
 * server on). One question per screen: the company → its pay way (only "급여 방식 있음 · 맞아요 n명", 결정 D79) → used.
 */
@Composable
fun PayFindEntry(modifier: Modifier = Modifier, open: Boolean = false) {
    val c = LocalContext.current.appContainer
    if (!c.companyApi.enabled) return
    var showing by rememberSaveable { mutableStateOf(open) }
    OutlinedButton(onClick = { showing = true }, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Business, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("우리 회사 급여 방식 찾기")
    }
    if (showing) PayFindSheet(onDismiss = { showing = false })
}

private enum class PayFindStep { NAME, CONFIRM, NONE, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayFindSheet(onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val find = remember(c) { PayFind(c) }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(find.known()) }
    var step by rememberSaveable { mutableStateOf(PayFindStep.NAME) }
    var picked by remember { mutableStateOf<CompanyHit?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<PayFind.Used?>(null) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                PayFindStep.NAME -> {
                    Text("어느 회사에서 일하세요?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("같은 회사 분들이 확인한 급여 계산 방식이 있으면 가져와요. 금액은 내 것만 넣어요.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("회사 이름") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth())
                    val q = name.trim()
                    val ready = CompanyText.norm(q).length >= 2
                    val hits by produceState<List<CompanyHit>?>(null, q) {
                        value = null
                        if (!ready) return@produceState
                        delay(INLINE_PAUSE_MS)
                        value = withTimeoutOrNull(INLINE_WAIT_MS) { inlineSearch(c, q) } ?: emptyList()
                    }
                    val list = hits
                    when {
                        !ready -> Unit
                        list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        else -> {
                            list.take(5).forEach { hit -> PayHitRow(hit) { picked = hit; error = null; step = if (hit.lead?.pay == true) PayFindStep.CONFIRM else PayFindStep.NONE } }
                            OutlinedButton(onClick = { picked = null; step = PayFindStep.NONE }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (list.isEmpty()) "‘$q’ 급여 방식이 없어요 · 직접 설정" else "여기 없어요, 직접 설정")
                            }
                        }
                    }
                }
                PayFindStep.CONFIRM -> {
                    val hit = picked ?: return@Column
                    val lead = hit.lead
                    Text("‘${hit.name}’ 급여 방식을 쓸까요?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    // 결정 D79: before using it, only that there is one and how many said 맞아요 — never its values.
                    StatusChip("급여 방식 있음 · 맞아요 ${lead?.ok ?: 0}명", Tone.GOOD)
                    if (lead != null && lead.dept.isNotEmpty()) Text("${lead.dept}의 대표 주기에 딸린 방식이에요.", style = MaterialTheme.typography.bodyMedium)
                    Text("쓰면 이 회사 광장에 참여되고, 수당과 계산 방식이 들어와요. 기본급 같은 금액은 다음 화면에서 내 것만 넣어요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        busy = true
                        scope.launch {
                            try {
                                done = find.use(hit)
                                step = if (done == PayFind.Used.Gone) PayFindStep.NONE else PayFindStep.DONE
                            } catch (e: CompanyException) {
                                error = companyProblem(e.code, e.field)
                            } catch (e: com.bamnat.app.data.BackupException) {
                                error = e.message ?: "급여 방식을 넣지 못했어요"
                            }
                            busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("이 급여 방식 쓰기")
                    }
                    TextButton(onClick = { step = PayFindStep.NAME }) { Text("다른 회사 찾기") }
                }
                PayFindStep.NONE -> {
                    val company = picked?.name ?: name.trim()
                    Text("아직 ‘$company’ 급여 방식이 없어요", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("간편 설정으로 내 급여를 맞춰 주세요. 다 맞추면 같은 회사 분들도 쓰게 나눌지 한 번 여쭤봐요(금액·이름은 빼고).",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { find.none(company); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("간편 설정으로 만들기") }
                    TextButton(onClick = { step = PayFindStep.NAME }) { Text("다른 회사 찾기") }
                }
                PayFindStep.DONE -> {
                    Text(if (done == PayFind.Used.Applied) "급여 방식을 넣었어요" else "이제 내 금액만 넣으면 돼요",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(if (done == PayFind.Used.Applied) "내 금액은 그대로 두고 수당과 계산 방식만 바꿨어요." else "‘${picked?.name.orEmpty()}’ 방식으로 계산해요. 기본급과 수당 금액만 물어볼게요.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (done == PayFind.Used.Applied) "확인" else "내 금액 넣기") }
                }
            }
        }
    }
}

@Composable
private fun PayHitRow(hit: CompanyHit, onClick: () -> Unit) {
    SoftCard(Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hit.name, style = MaterialTheme.typography.titleSmall)
                val lead = hit.lead
                Text(if (lead?.pay == true) "급여 방식 있음 · 맞아요 ${lead.ok}명" else "급여 방식은 아직 없어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lead?.pay == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
