package com.bamnat.app.ui.recipe

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.recipe.RecipeLinks
import com.bamnat.core.recipe.PieceKind

/** [RCP-19] '이 설정 나누기' on the screen a piece comes from; gone while 나눔 판 is off (설정 › 나눔 판). */
@Composable
fun ShareThisSetting(kind: PieceKind, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val on by c.recipes.on.collectAsStateWithLifecycle()
    if (!on || !c.recipeApi.enabled) return
    TextButton(onClick = { RecipeLinks.share(kind) }, modifier = modifier) { Text("이 설정 나누기 (나눔 판)") }
}
