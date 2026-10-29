package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle

/**
 * [RCP-01] 1.34 나눔 판 (계획 1.34-개발계획.md, 설계서 4-1): a recipe is what one user shares of their own settings for others
 * to look at and put on their calendar. It holds values only (결정 D106): pieces of settings the app already knows
 * ([PieceCatalog]), the fields the receiver fills in ([Input]) and named colours ([Token]). No formula, script, link or picture
 * address goes in one, so a recipe can neither carry nor reveal how the app works; the app's own engines do every calculation.
 *
 * A recipe is read only through [RecipeCodec], which drops whatever the catalog does not know and refuses what is out of range;
 * the server's `cleanRecipe()` does the same with the cases in `docs/recipe-cases.json`.
 */
data class Recipe(
    val title: String,
    /** One line under the title in lists (60 letters). */
    val summary: String = "",
    /** 누구에게 좋아요 · 적용하면 바뀌는 것 · 주의할 점 (600 letters, 20 lines). */
    val desc: String = "",
    /** The kinds of work it is made for; each piece applies only where it fits ([PieceKind.styles], 유형 렌즈). */
    val styles: Set<WorkStyle>,
    val cover: Cover = Cover(),
    val tokens: List<Token> = emptyList(),
    val inputs: List<Input> = emptyList(),
    val pieces: List<Piece>,
    /** [RCP-46] 1.35: the recipe this one was made from (고쳐 만들기), by its code; null for an original. */
    val parent: String? = null,
) {
    /** The kinds of work a piece of it applies to, as [style] sees it (유형 렌즈). */
    fun piecesFor(style: WorkStyle?): List<Piece> = pieces.filter { style == null || style in it.kind.styles }

    /** The pieces [style] cannot use: folded under "이 유형에는 없어요". */
    fun piecesNotFor(style: WorkStyle?): List<Piece> = if (style == null) emptyList() else pieces.filter { style !in it.kind.styles }

    fun input(id: String): Input? = inputs.firstOrNull { it.id == id }
    fun token(name: String): Token? = tokens.firstOrNull { it.name == name }

    companion object {
        const val FORMAT = "bamnat-recipe"
        /** The format this app writes and the newest it reads; a newer one asks for an update. */
        const val VERSION = 1
    }
}

/**
 * 표지: two colours in a gradient ([dir] 0 위→아래, 1 왼→오, 2 대각선 ↘, 3 대각선 ↙) and one big emoji; from 1.35 [image] too, a
 * picture the server keeps by id ([RCX-15]), drawn over the gradient once the operator let it show.
 */
data class Cover(val a: Long = 0xFF3F51B5L, val b: Long = 0xFF9C27B0L, val dir: Int = 0, val emoji: String = "🗓️", val image: String? = null) {
    companion object {
        val IMAGE = Regex("[0-9a-f]{16}")
    }
}

/**
 * 테마 토큰 (KWGT 전역처럼): a named colour for light and dark screens. A colour field anywhere in the recipe may point at a
 * token instead of holding a colour, so changing the token changes all of them at once.
 */
data class Token(val name: String, val light: Long, val dark: Long)

/** The kinds of field a receiver fills in. */
enum class InputKind(val key: String, val label: String) {
    MONEY("money", "금액"),
    NUMBER("number", "숫자"),
    TIME("time", "시각"),
    COLOR("color", "색"),
    EMOJI("emoji", "이모지"),
    CHOICE("choice", "고르기"),
    TEXT("text", "글"),
    FLAG("flag", "켜기·끄기");

    companion object {
        fun of(key: String): InputKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 받는 사람이 정할 칸 (Home Assistant 블루프린트의 입력): [id] is made by the app (`i1`, `i2` …) and never changes once the recipe
 * is out, so a new version does not break those who use it (Anki·HA에서 배운 것). [default] is null for "비워 두기".
 */
data class Input(
    val id: String,
    val label: String,
    val kind: InputKind,
    val default: Any? = null,
    val min: Double? = null,
    val max: Double? = null,
    val required: Boolean = false,
    val choices: List<String> = emptyList(),
    /** A line on where the value comes from (최저임금 2026 등), shown under the field. */
    val hint: String = "",
)

/** One piece of settings: its kind and its values, as [PieceCatalog] checked them. */
data class Piece(val kind: PieceKind, val values: Map<String, Any?>)

/**
 * A field's value that is not written in the recipe: the receiver's answer to an input ([Ref.Input]) or a theme token's colour
 * ([Ref.Token]). On the wire `{"input":"i1"}` and `{"token":"밤색"}`.
 */
sealed interface Ref {
    data class Input(val id: String) : Ref
    data class Token(val name: String) : Ref
}

/** Why a recipe could not be read: a short [code] and where ([path]), never the text of the recipe. */
class RecipeException(val code: String, val path: String = "") : Exception("$code at $path")
