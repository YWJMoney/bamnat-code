package com.bamnat.app.recipe

/**
 * [DRW-01] 1.37 내 서랍 (사용자 2026-11-04 "테마 탭은 개인 보관함"): everything of mine that 마을 › 테마 keeps, in one list — the themes I
 * made and the studio drafts, the recipes put on from 장터, the themes worn before and the parts kept aside. One thing's place in the list.
 */
enum class DrawerKind(val label: String, val short: String) {
    MINE("내가 만든 것", "내 것"),
    GOT("받은 것", "받음"),
    WORN("입었던 것", "입었던"),
    PART("담아 둔 조각", "조각"),
}

/**
 * One thing in 내 서랍. [key] is the same across launches ("theme:<wear key>", "draft:<id>", "got:<code>", "part:<id>"), so its 묶음
 * stays with it; [at] is when it came in or was last changed (millis); [words] what else 찾기 matches (the kind of 조각, the theme it came
 * from).
 */
data class DrawerItem(val key: String, val kind: DrawerKind, val title: String, val at: Long, val folder: String? = null, val words: String = "")

object Drawer {
    enum class Sort(val label: String) { RECENT("최근 것부터"), NAME("이름순") }

    /** The 묶음 filter for things in no 묶음. */
    const val NO_FOLDER = ""

    /**
     * What shows for [kind] (null = 전체), [folder] (null = 모든 묶음, [NO_FOLDER] = 묶음 없는 것) and [query] (any of its words, spaces
     * and case aside), in [sort] order. Newest first breaks ties in 이름순 too, so the order never jumps between launches.
     */
    fun shown(items: List<DrawerItem>, kind: DrawerKind?, folder: String?, query: String, sort: Sort): List<DrawerItem> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val picked = items.filter { i ->
            (kind == null || i.kind == kind) &&
                (folder == null || (i.folder ?: NO_FOLDER) == folder) &&
                words.all { w -> squash("${i.title} ${i.words} ${i.folder.orEmpty()} ${i.kind.label}").contains(squash(w)) }
        }
        val recent = compareByDescending<DrawerItem> { it.at }.thenBy { it.key }
        return when (sort) {
            Sort.RECENT -> picked.sortedWith(recent)
            Sort.NAME -> {
                val ko = java.text.Collator.getInstance(java.util.Locale.KOREAN)
                picked.sortedWith(Comparator<DrawerItem> { a, b -> ko.compare(nameKey(a.title), nameKey(b.title)) }.then(recent))
            }
        }
    }

    /** Each kind with how many of it there are, for the chips (전체 first as null). */
    fun counts(items: List<DrawerItem>): List<Pair<DrawerKind?, Int>> =
        listOf<Pair<DrawerKind?, Int>>(null to items.size) + DrawerKind.entries.map { k -> k to items.count { it.kind == k } }

    /** The 묶음 names in use, in 가나다 order. */
    fun folders(items: List<DrawerItem>): List<String> {
        val ko = java.text.Collator.getInstance(java.util.Locale.KOREAN)
        return items.mapNotNull { it.folder?.takeIf { f -> f.isNotBlank() } }.distinct().sortedWith(ko)
    }

    /** 이름순 reads from the first letter, so ‘…’의 조각 goes by its name, not its quote mark. */
    private fun nameKey(s: String) = s.dropWhile { !it.isLetterOrDigit() }

    private fun squash(s: String) = s.lowercase().filterNot { it.isWhitespace() }
}
