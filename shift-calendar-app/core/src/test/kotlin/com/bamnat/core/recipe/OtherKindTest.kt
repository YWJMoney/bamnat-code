package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0.0 [RVM-42] 총괄 HQ-100: a work for other kinds of work than mine says whose, in the kinds' short names. */
class OtherKindTest {
    @Test fun whoseItIs() {
        assertEquals("일근용", RecipeDescribe.otherKind(setOf(WorkStyle.DAY), WorkStyle.SHIFT))
        assertEquals("일근·알바용", RecipeDescribe.otherKind(setOf(WorkStyle.HOURLY, WorkStyle.DAY), WorkStyle.SHIFT))
        assertNull("mine too", RecipeDescribe.otherKind(setOf(WorkStyle.DAY, WorkStyle.SHIFT), WorkStyle.SHIFT))
        assertNull("for everyone", RecipeDescribe.otherKind(emptySet(), WorkStyle.SHIFT))
        assertNull("no kind of work picked", RecipeDescribe.otherKind(setOf(WorkStyle.DAY), null))
    }
}
