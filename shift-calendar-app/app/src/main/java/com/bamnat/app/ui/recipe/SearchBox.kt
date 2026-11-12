package com.bamnat.app.ui.recipe

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVM-31] 찾기 칸 (총괄 HQ-42, 조건 1·5): the box 장터 and 내 서랍 are searched by, one shape in both. An outlined pill 48dp
 * high (a form's text field is 56): the magnifier at the start, the ask on one line (cut with … rather than going to two at big letters;
 * give a short [placeholder] there), and once something is typed a ✕ that clears it ('찾기 지우기'). Kept in 장터's package: a search box
 * for every list is 갈래 P's to make (docs/design/공통-부품.md, 맨 위), and this one is offered to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    onSearch: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.heightIn(min = 48.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(color = if (focused) colors.focusedTextColor else colors.unfocusedTextColor),
            cursorBrush = SolidColor(colors.cursorColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
            interactionSource = interaction,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = inner,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interaction,
                    placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Clear, "찾기 지우기") } }) else null,
                    colors = colors,
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 12.dp, bottom = 12.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(enabled = true, isError = false, interactionSource = interaction, colors = colors,
                            shape = MaterialTheme.shapes.extraLarge)
                    },
                )
            },
        )
    }
}
