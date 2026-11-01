package com.bamnat.app.recipe

import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.parts.CellParts
import com.bamnat.app.ui.parts.PartTheme
import com.bamnat.app.ui.parts.dayStates
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

/** [BLU-05][BLU-06] 1.36 부품 꾸미기 on the phone: a recipe's parts put on and taken back, kept as a look, drawn by the engine. */
@RunWith(AndroidJUnit4::class)
class PartsApplyTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    private val lines = listOf(
        mapOf("id" to "app.button", "fill" to 0xFF00897BL),
        mapOf("id" to "app.card", "r" to 20L),
        mapOf("id" to "cal.cell", "on" to "role.night", "line" to "dash", "lineC" to 0xFFE5484DL),
        mapOf("id" to "cal.cell", "order" to "mark,date", "dir" to "col"),
        mapOf("id" to "cal.cell.date", "on" to "today", "fill" to 0xFFFFC107L),
    )

    @Test fun partsPutOnThenTakenBack() = runBlocking {
        val before = c.settingsRepository.current.parts
        val r = Recipe(title = "부품 시험", styles = setOf(WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.PARTS, mapOf("parts" to lines))))
        // The recipe goes through the codec as one from the server would.
        val read = RecipeCodec.read(RecipeCodec.write(r))
        val id = c.recipes.apply(read, "PART2345", 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.PARTS, RecipePieces.Mode.REPLACE)), emptyMap())
        assertNotNull(id)
        val bp = Blueprint.decode(c.settingsRepository.current.parts)
        assertEquals(0xFFE5484DL, bp.style("cal.cell", setOf("role.night")).lineC)
        assertEquals("dash", bp.style("cal.cell", setOf("role.night", "sun")).line)
        // Exported again it is the same piece, so 올리기 sends what is on the phone.
        val out = c.recipes.pieces.export(PieceKind.PARTS, PersonalCalendar.MAIN)!!
        assertEquals(bp.sorted(), out["parts"])
        assertTrue(c.recipes.undo(id!!))
        assertEquals(before, c.settingsRepository.current.parts)
    }

    @Test fun theStudioDraftCarriesParts() = runBlocking {
        val draft = c.recipes.pieces.plainLook(c.settingsRepository.current).copy(parts = Blueprint(Blueprint.clean(lines)).encode())
        val pieces = c.recipes.pieces.lookPieces(draft)
        val p = pieces.single { it.kind == PieceKind.PARTS }
        val again = c.recipes.pieces.onDraft(c.recipes.pieces.plainLook(draft), PieceKind.PARTS, p.values)
        assertEquals(draft.parts, again.parts)
        assertTrue(c.recipes.pieces.lookDiff(draft, c.recipes.pieces.plainLook(draft)) >= 1)
    }

    @Test fun theEngineFindsEachCellsParts() {
        val bp = Blueprint(Blueprint.clean(lines))
        val today = dayStates("night", DayOfWeek.SUNDAY, holiday = false, inMonth = true, today = true, selected = false)
        assertEquals(setOf("role.night", "sun", "today"), today)
        val cell = CellParts.of(bp, today)!!
        assertEquals(listOf("mark", "date"), cell.order.take(2))
        assertEquals(0xFFFFC107L, cell.date.fill)
        // A blueprint that leaves the cell's inside alone draws the cell as before.
        assertNull(CellParts.of(Blueprint(Blueprint.clean(listOf(mapOf("id" to "cal.cell", "fill" to 0xFF000000L)))), emptySet()))
        // App-wide parts go through the theme: buttons take the colour, cards the corners.
        val scheme = PartTheme.colors(lightColorScheme(), bp)
        assertEquals(0xFF00897BL.toInt(), scheme.primary.toArgb())
        val shapes = PartTheme.shapes(Shapes(), bp)
        assertTrue(shapes.medium != Shapes().medium)
        // An empty blueprint leaves the theme as it was.
        val plain = lightColorScheme()
        assertSame(plain, PartTheme.colors(plain, Blueprint.EMPTY))
    }
}
