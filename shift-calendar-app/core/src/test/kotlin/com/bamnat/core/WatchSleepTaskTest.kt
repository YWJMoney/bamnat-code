package com.bamnat.core

import com.bamnat.core.work.WatchDays
import com.bamnat.core.work.WatchPayload
import com.bamnat.core.work.WatchSleep
import com.bamnat.core.work.WatchTask
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0.0 [RVI-94][RVI-95] (검토 I03b-04·I07ab-04) The watch's 잠 and 오늘 할 일 tiles: what the phone sends and what the wrist sends back. */
class WatchSleepTaskTest {
    private val at = LocalDateTime.of(2026, 11, 13, 1, 20)
    private val tasks = listOf(
        WatchTask(7, "약 받기", LocalDate.of(2026, 11, 12), done = false, late = true),
        WatchTask(9, "장보기\t우유", LocalDate.of(2026, 11, 13), done = false, late = false),
        WatchTask(11, "택배", null, done = true, late = false),
    )

    @Test fun theDaysCarryTheSleepAndTheToDos() {
        val p = WatchPayload(emptyList(), at, sleepOn = true, asleepSince = at, tasks = tasks, taskHead = "할 일 2개 · 늦은 것 1개")
        val back = WatchDays.decode(WatchDays.encode(p))!!
        assertEquals(true, back.sleepOn)
        assertEquals(at, back.asleepSince)
        assertEquals(listOf(7L, 9L, 11L), back.tasks.map { it.id })
        // A tab in the words does not break the line the days ride on.
        assertEquals("장보기 우유", back.tasks[1].text)
        assertEquals(listOf(true, false, false), back.tasks.map { it.late })
        assertEquals(listOf(false, false, true), back.tasks.map { it.done })
        assertNull(back.tasks[2].day)
        assertEquals("할 일 2개 · 늦은 것 1개", back.taskHead)
        // A copy from an older phone has none of them.
        val old = WatchDays.encode(p).lines().first().split('\t').take(17).joinToString("\t")
        val oldBack = WatchDays.decode(old)!!
        assertEquals(false, oldBack.sleepOn)
        assertNull(oldBack.asleepSince)
        assertEquals(emptyList<WatchTask>(), oldBack.tasks)
    }

    @Test fun thePressFromTheWristReadsBack() {
        val press = WatchSleep.Press(asleep = true, atMillis = 1_763_000_000_000, id = "w1763000000000")
        assertEquals(press, WatchSleep.decode(WatchSleep.encode(press)))
        assertNull(WatchSleep.decode("napping\t1\tx"))
        assertNull(WatchSleep.decode("asleep\t0\tx"))
        // The wrist's own press shows until the phone's copy comes.
        val p = WatchPayload(emptyList(), at, sleepOn = true, asleepSince = null)
        assertEquals(at, WatchSleep.asleepNow(p, pressed = true, pressedAt = at))
        assertNull(WatchSleep.asleepNow(p.copy(asleepSince = at), pressed = false, pressedAt = at))
        assertEquals(at, WatchSleep.asleepNow(p.copy(asleepSince = at), pressed = null, pressedAt = null))
    }

    @Test fun theTileShowsThreeOpenOnesAndHowManyMore() {
        val many = (1L..6L).map { WatchTask(it, "일 $it", null, done = it == 2L, late = false) }
        val (rows, more) = WatchTask.tile(many, pressed = setOf(3L))
        assertEquals(listOf(1L, 4L, 5L), rows.map { it.id })
        assertEquals(1, more)
        assertEquals(Triple(9L, LocalDate.of(2026, 11, 13), true), WatchTask.readDone(WatchTask.doneMessage(tasks[1], true)))
        assertEquals(Triple(11L, null, false), WatchTask.readDone(WatchTask.doneMessage(tasks[2], false)))
    }
}
