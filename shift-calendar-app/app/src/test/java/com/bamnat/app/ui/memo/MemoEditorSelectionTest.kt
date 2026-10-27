package com.bamnat.app.ui.memo

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.MainActivity
import com.bamnat.app.ui.components.LocalEdgeScroll
import com.bamnat.app.ui.components.LongTextField
import com.bamnat.app.ui.components.edgeScrollViewport
import com.bamnat.app.ui.components.rememberEdgeScroll
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.consent.ConsentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [MEMO-SEL-01][MEMO-SEL-02][MEMO-SEL-04] 1.30 AY: a selection handle held at the memo's edge scrolls the memo and the
 * selection keeps growing; typing still brings the caret into view and a caret alone never starts the edge scroll.
 * A handle's drag reaches the text box as a selection change with the text the same, which is what the semantics
 * "set selection" sends too, so that stands in for the finger here.
 */
@RunWith(AndroidJUnit4::class)
// Real text measuring (lines, widths), as the screenshots draw it.
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class MemoEditorSelectionTest {
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val body = (1..60).joinToString("\n") { "${it}번째 줄, 인수인계 메모" }

    private fun memo(test: (ActivityScenario<MainActivity>) -> Unit) {
        app.container.consentStore.markSeen(ConsentItem.entries)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a ->
                val vm = MemoEditorViewModel(app.container, 0L, null, false)
                vm.setBody(body)
                a.setContent { Touch { BamnatTheme { MemoEditorScreen(vm, onBack = {}) } } }
            }
            compose.waitForIdle()
            test(s)
        }
    }

    /** A finger, not a keyboard: Robolectric's window starts out of touch mode, a phone in it. */
    @androidx.compose.runtime.Composable
    private fun Touch(content: @androidx.compose.runtime.Composable () -> Unit) {
        val touch = remember {
            object : androidx.compose.ui.input.InputModeManager {
                override val inputMode = androidx.compose.ui.input.InputMode.Touch
                override fun requestInputMode(inputMode: androidx.compose.ui.input.InputMode) = inputMode == this.inputMode
            }
        }
        CompositionLocalProvider(androidx.compose.ui.platform.LocalInputModeManager provides touch, content = content)
    }

    private fun field(): SemanticsNodeInteraction = compose.onAllNodes(hasSetTextAction())[1]
    private fun scroller(): SemanticsNodeInteraction =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    private fun scroll(): Float = scroller().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun selection(f: SemanticsNodeInteraction = field()): TextRange =
        f.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    private fun layout(f: SemanticsNodeInteraction): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        f.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(out) }
        return out.single()
    }

    /** The character at [fromTop] px under the top of what shows (negative: above its bottom). */
    private fun offsetAt(f: SemanticsNodeInteraction, fromTop: Float): Int {
        val port = scroller().fetchSemanticsNode().boundsInRoot
        // Not boundsInRoot: that is cut to what shows; the text's own top may be far above.
        val top = f.fetchSemanticsNode().positionInRoot.y
        val y = (if (fromTop >= 0) port.top + fromTop else port.bottom + fromTop) - top
        return layout(f).getOffsetForPosition(Offset(8f, y))
    }

    private fun touch(f: SemanticsNodeInteraction) {
        f.performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()
    }

    private fun frames(ms: Long = 100) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test fun theLowerHandleAtTheBottomScrollsAndTheSelectionGrows() = memo {
        val f = field()
        touch(f)
        compose.mainClock.autoAdvance = false
        f.performTextInputSelection(TextRange(10, 30))
        frames()
        val start = scroll()
        val atBottom = offsetAt(f, -6f)
        f.performTextInputSelection(TextRange(10, atBottom))
        var lastScroll = start
        var lastEnd = atBottom
        repeat(5) {
            frames(200)
            val now = scroll()
            val sel = selection()
            assertTrue("스크롤이 내려가요 ($lastScroll → $now)", now > lastScroll)
            assertTrue("선택이 늘어나요 ($lastEnd → ${sel.end})", sel.end > lastEnd)
            assertEquals("시작은 그대로", 10, sel.start)
            lastScroll = now
            lastEnd = sel.end
        }
        // The finger lifts (the copy bar comes up): nothing moves after.
        compose.mainClock.autoAdvance = true
    }

    @Test fun theUpperHandleAtTheTopScrollsUp() = memo {
        val f = field()
        touch(f)
        compose.mainClock.autoAdvance = false
        scroller().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 2_000f) }
        frames()
        val down = scroll()
        assertTrue(down > 0f)
        val end = body.length - 5
        val atTop = offsetAt(f, 6f)
        f.performTextInputSelection(TextRange(atTop + 3, end))
        frames()
        f.performTextInputSelection(TextRange(atTop, end))
        var lastScroll = scroll()
        var lastStart = atTop
        repeat(4) {
            frames(200)
            val now = scroll()
            val sel = selection()
            assertTrue("스크롤이 올라가요 ($lastScroll → $now)", now < lastScroll)
            assertTrue("선택이 위로 늘어나요 ($lastStart → ${sel.start})", sel.start < lastStart)
            assertEquals("끝은 그대로", end, sel.end)
            lastScroll = now
            lastStart = sel.start
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun aCaretAtTheEdgeIsNotADragAndTypingStillFollows() = memo {
        val f = field()
        touch(f)
        compose.mainClock.autoAdvance = false
        val atBottom = offsetAt(f, -6f)
        f.performTextInputSelection(TextRange(atBottom))
        // The caret is brought into view once (as before), then the memo stays still.
        frames(600)
        val settled = scroll()
        frames(1_000)
        assertEquals(settled, scroll(), 0.5f)
        compose.mainClock.autoAdvance = true
        // Typing at the very end, far below: the line being typed comes into view.
        f.performTextInputSelection(TextRange(body.length))
        f.performTextInput("끝")
        compose.waitForIdle()
        val port = scroller().fetchSemanticsNode().boundsInRoot
        val top = f.fetchSemanticsNode().positionInRoot.y
        val caret = layout(f).getCursorRect(body.length + 1)
        assertTrue("입력 중인 줄이 보여요 ($top + ${caret.bottom} vs ${port.bottom}, scroll ${scroll()})", top + caret.bottom <= port.bottom + 1f)
    }

    /** [MEMO-SEL-02] The same part in another long text box (문의, 일지): its page scrolls too. */
    @Test fun aLongTextFieldOnAScrollingPageScrollsItToo() {
        app.container.consentStore.markSeen(ConsentItem.entries)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a ->
                a.setContent { Touch {
                    BamnatTheme {
                        val scroll = rememberScrollState()
                        val edge = rememberEdgeScroll(scroll)
                        var text by remember { mutableStateOf(body) }
                        CompositionLocalProvider(LocalEdgeScroll provides edge) {
                            Column(Modifier.fillMaxWidth().height(400.dp).edgeScrollViewport(edge).verticalScroll(scroll)) {
                                Text("2. 무슨 일인지 적어 주세요")
                                LongTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 5)
                            }
                        }
                    }
                } }
            }
            compose.waitForIdle()
            val f = compose.onAllNodes(hasSetTextAction())[0]
            touch(f)
            compose.mainClock.autoAdvance = false
            f.performTextInputSelection(TextRange(4, 20))
            frames()
            val start = scroll()
            f.performTextInputSelection(TextRange(4, offsetAt(f, -6f)))
            frames(600)
            assertTrue("${scroll()} > $start", scroll() > start)
            assertEquals(4, selection(f).start)
            compose.mainClock.autoAdvance = true
        }
    }
}
