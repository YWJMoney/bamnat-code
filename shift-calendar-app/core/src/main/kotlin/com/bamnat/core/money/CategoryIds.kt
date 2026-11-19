package com.bamnat.core.money

import com.bamnat.core.work.WorkStyle

/**
 * 2.0.0 [RVK-60] (검토 I06ab-01, DB 37): the categories the sums read by what they are, not by what they are called. Each keeps
 * its fixed [id] ([CategoryIds]), so 급여 renamed 월급 still counts as pay once and 카드값 renamed 카드 대금 still fills itself.
 */
enum class CategoryRole(val kind: MoneyKind, val builtin: String) {
    SALARY(MoneyKind.INCOME, MoneyCategories.SALARY),
    CARD(MoneyKind.TRANSFER, "카드값"),
    OWN(MoneyKind.TRANSFER, "내 계좌"),
    INTEREST(MoneyKind.EXPENSE, MoneyCategories.INTEREST),
    LOAN(MoneyKind.TRANSFER, "대출 상환");

    val id: String get() = CategoryIds.builtinId(kind, builtin)
}

/**
 * 2.0.0 [RVK-60] Whose money a category is: 집(가정) or 일·가게(사업). [FOLLOW] leaves it to the category (the built-in work ones are
 * 일·가게) and, for one the user made, to the calendar's kind of work. A shared ledger never shares 일·가게 ones (공동 가계부 설계).
 */
enum class CategoryScope(val code: Int) {
    FOLLOW(0), HOME(1), WORK(2);

    companion object {
        fun of(code: Int): CategoryScope = entries.firstOrNull { it.code == code } ?: FOLLOW
    }
}

/**
 * 2.0.0 [RVK-60] (검토 I06ab-01) A category's fixed id: lines point at it, and the name is only what is shown.
 * - `b.<kind>.<name>`: a built-in one, named as the app first named it, whatever the user calls it now.
 * - `n.<kind>.<name>`: a category of a phone before 37 that was not a built-in one (or was renamed then), named as it was at 37.
 * - `u.<letters>`: one the user made from 37 on.
 * Renamed, a category keeps its id, so its lines, the sums and (later) a shared ledger and 같은 유형 비교 follow it.
 */
object CategoryIds {
    fun code(kind: MoneyKind): String = when (kind) { MoneyKind.EXPENSE -> "x"; MoneyKind.INCOME -> "i"; MoneyKind.TRANSFER -> "t" }

    private fun kindOf(code: String): MoneyKind? = MoneyKind.entries.firstOrNull { code(it) == code }

    private val builtins: Map<MoneyKind, Set<String>> = MoneyKind.entries.associateWith { k ->
        (MoneyCategories.of(k) + MoneyCategories.of(k, WorkStyle.FIELD) + MoneyCategories.of(k, WorkStyle.BUSINESS)).toSet()
    }

    /** Every name the app has ever offered of itself for [kind]. */
    fun builtinNames(kind: MoneyKind): Set<String> = builtins.getValue(kind)

    fun builtinId(kind: MoneyKind, name: String): String = "b.${code(kind)}.$name"

    /** The id [name] has when nobody gave it one: its built-in id, else the id it takes from its name. */
    fun of(kind: MoneyKind, name: String): String = if (name in builtinNames(kind)) builtinId(kind, name) else "n.${code(kind)}.$name"

    /** A new id for a category the user makes. */
    fun own(random: kotlin.random.Random = kotlin.random.Random.Default): String =
        "u." + (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)] }.joinToString("")

    /** The name a built-in id was first given; null for any other. */
    fun builtinName(id: String): String? = if (id.startsWith("b.") && id.length > 4 && id[3] == '.') id.substring(4) else null

    /** The name in a `b.` or `n.` id; null for one the user made. */
    fun nameIn(id: String): String? = if ((id.startsWith("b.") || id.startsWith("n.")) && id.length > 4 && id[3] == '.') id.substring(4) else null

    /** The kind in a `b.` or `n.` id. */
    fun kindIn(id: String): MoneyKind? = if (id.length > 4 && id[1] == '.' && id[3] == '.') kindOf(id.substring(2, 3)) else null

    fun role(id: String): CategoryRole? = CategoryRole.entries.firstOrNull { it.id == id }

    /** Whether a line or part ([id], shown as [name]) is [role]'s; a line saved before 37 without an id goes by its name. */
    fun matches(id: String, name: String, role: CategoryRole): Boolean = if (id.isNotEmpty()) id == role.id else name == role.builtin

    /** The id behind [name] among [edits] (the user's categories), else [of]. */
    fun resolve(kind: MoneyKind, name: String, edits: List<CategoryEdit>): String =
        edits.firstOrNull { it.kind == kind && it.name == name && it.id.isNotEmpty() }?.id ?: of(kind, name)

    /** What [id] is called now: the user's name for it, else the name in it; null for an id nobody has. */
    fun nameOf(kind: MoneyKind, id: String, edits: List<CategoryEdit>): String? =
        edits.firstOrNull { it.kind == kind && it.id == id }?.name ?: nameIn(id)?.takeIf { kindIn(id) == kind }

    /**
     * The id a line of [kind] shown as [name] keeps: [hint] (the id it had) while that is still called [name], else the one [name]
     * stands for now. A line moved to another category in the sheet keeps its old id with its new name, so the name wins there.
     */
    fun settle(kind: MoneyKind, name: String, hint: String, edits: List<CategoryEdit>): String =
        if (hint.isNotEmpty() && nameOf(kind, hint, edits) == name) hint else resolve(kind, name, edits)

    /** The work categories the app offers field and shop calendars; 일·가게 money by itself. */
    private val workNames: Map<MoneyKind, Set<String>> = MoneyKind.entries.associateWith { k ->
        (MoneyCategories.of(k, WorkStyle.FIELD) + MoneyCategories.of(k, WorkStyle.BUSINESS)).toSet() - MoneyCategories.of(k).toSet()
    }

    /** Whose money [id] is: the user's choice ([scope]), else a built-in work one is 일·가게, else the calendar's ([style]). */
    fun scopeOf(kind: MoneyKind, id: String, scope: CategoryScope, style: WorkStyle?): CategoryScope = when {
        scope != CategoryScope.FOLLOW -> scope
        builtinName(id)?.let { it in workNames.getValue(kind) } == true -> CategoryScope.WORK
        builtinName(id) != null -> CategoryScope.HOME
        style == WorkStyle.FIELD || style == WorkStyle.BUSINESS -> CategoryScope.WORK
        else -> CategoryScope.HOME
    }

    /**
     * The 표준 묶음 a built-in category belongs to, for 같은 유형 비교 later (LEDH-37, 결정 23): one the user made has none until they
     * join it to one. Keys never change; what is shown for them is the app's.
     */
    fun standard(id: String): String {
        val name = builtinName(id) ?: return ""
        return STANDARD.entries.firstOrNull { name in it.value }?.key ?: "etc"
    }

    private val STANDARD: Map<String, Set<String>> = mapOf(
        "food" to setOf("식비", "카페", "편의점", "배달", "현장식대"),
        "shopping" to setOf("장보기·쇼핑", "생활", "의류·미용"),
        "transport" to setOf("교통", "자동차", "유류비", "차량유지", "톨비·주차"),
        "home" to setOf("주거", "공과금", "통신", "구독"),
        "health" to setOf("의료", "보험"),
        "family" to setOf("경조사", "육아", "반려동물"),
        "learning" to setOf("교육", "여가"),
        "finance" to setOf(MoneyCategories.INTEREST, "대출이자"),
        "work_cost" to setOf("자재비", "공구·장비", "작업복·안전", "숙박", "외주·인건비", "플랫폼수수료", "소모품", "재료비", "포장·소모품",
            "임대료", "관리비", "인건비", "4대보험", "카드수수료", "배달앱수수료", "광고·마케팅", "장비·수리", "세금"),
        "pay" to setOf(MoneyCategories.SALARY, "상여", "일당"),
        "side" to setOf("부수입", "판매", "용돈", "환급", "이자"),
        "work_income" to setOf("일감 수입", "선금", "잔금", "카드매출", "현금매출", "계좌이체", "배달앱매출"),
        "move" to setOf("저축", "카드값", "내 계좌", "투자", "대출 상환", "사장님 인출"),
    )
}
