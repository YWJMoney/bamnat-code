package com.bamnat.app.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.36 PC 재검토: every 창 keeps the app's 글자 크기, so none opens Material's or Compose's own dialog directly. */
class AppDialogsTest {
    private val ui = File("src/main/java/com/bamnat/app")
    private val direct = Regex("""androidx\.compose\.material3\.(AlertDialog|DatePickerDialog|BasicAlertDialog|DropdownMenu)\b|androidx\.compose\.ui\.window\.Dialog\b|androidx\.compose\.material3\.ModalBottomSheet\(""")

    @Test fun everyDialogKeepsTheAppsTextSize() {
        val found = ui.walkTopDown().filter { it.extension == "kt" && it.name != "AppDialogs.kt" && it.name != "AppSheet.kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (direct.containsMatchIn(l)) "${f.name}:${i + 1}" else null } }
            .toList()
        assertEquals("앱 글자 크기가 빠지는 창: components의 AlertDialog·Dialog·DatePickerDialog·AppSheet를 써요", emptyList<String>(), found)
    }

    /** PC 확인 1.36 (두 폰 '응답 없음'): a closed menu is not built, so a month of day cells each with a menu stays light. */
    @Test fun aClosedMenuIsNotBuilt() {
        val src = File(ui, "ui/components/AppDialogs.kt").readText()
        val menu = src.substringAfter("fun DropdownMenu(")
        assert(menu.indexOf("if (!opened[0]) return") in 0 until menu.indexOf("material3.DropdownMenu(")) { "AppDialogs.DropdownMenu builds material3's menu while closed" }
    }
}
