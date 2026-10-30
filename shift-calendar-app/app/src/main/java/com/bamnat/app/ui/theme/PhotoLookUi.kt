package com.bamnat.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bamnat.app.recipe.LookImages

/** [RCX-30] A 그림 꾸미기 picture by id, read off the main thread; null while it loads or when the phone does not have it. */
@Composable
fun rememberLookPicture(id: String?): Pair<ImageBitmap, Float>? {
    val context = LocalContext.current
    val picture by produceState<Pair<ImageBitmap, Float>?>(null, id) {
        value = LookImages.bitmap(context, id)?.let { it.asImageBitmap() to LookImages.lightness(it) }
    }
    return picture
}

/**
 * [RCX-30] The calendar's background picture behind the page, with a veil of the page's own colour over it so the dates read:
 * the user's dimming, and never less than 55 % when the picture is the opposite of the page (a dark photo on a light page).
 */
@Composable
fun BoxScope.PhotoBackdrop() {
    val look = LocalPhotoLook.current
    val (picture, light) = rememberLookPicture(look.bg) ?: return
    val surface = MaterialTheme.colorScheme.surface
    val pageLight = surface.luminance() >= 0.5f
    val opposite = (light < 0.5f) == pageLight
    val veil = (if (opposite) maxOf(look.dim, 55) else look.dim) / 100f
    Image(picture, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    Box(Modifier.matchParentSize().background(surface.copy(alpha = veil)))
}

/** [RCX-30] The picture on top of the calendar (3:1), in the app's corners; nothing when there is none. */
@Composable
fun PhotoHead(modifier: Modifier = Modifier) {
    val look = LocalPhotoLook.current
    val (picture, _) = rememberLookPicture(look.head) ?: return
    Image(
        picture, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).heightIn(max = 160.dp).aspectRatio(3f)
            .clip(MaterialTheme.shapes.large),
    )
}

/** [RCX-30] A kind of day's own picture, round, where its icon or emoji would go; null when it has none. */
@Composable
fun ShiftPicture(typeId: Long?, size: Dp): Boolean {
    val id = typeId?.let { LocalPhotoLook.current.icons[it] } ?: return false
    val (picture, _) = rememberLookPicture(id) ?: return false
    Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    return true
}
