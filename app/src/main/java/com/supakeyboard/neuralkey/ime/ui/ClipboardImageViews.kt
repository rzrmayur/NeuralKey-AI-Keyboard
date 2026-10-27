package com.supakeyboard.neuralkey.ime.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.ime.clipboard.ClipboardImageStore
import com.supakeyboard.neuralkey.ime.clipboard.ClipboardItem
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Down-sampled thumbnail of a stored clipboard image; decoded off the main thread. */
@Composable
fun ClipboardImageThumbnail(
    item: ClipboardItem,
    size: Dp,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { ClipboardImageStore(context) }
    val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 2).roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.imageFile, maxPx) {
        val name = item.imageFile
        value = if (name == null) null else withContext(Dispatchers.IO) {
            store.thumbnail(name, maxPx)?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.keyFunctionalBgColor)
            .semantics { contentDescription = "Copied image preview" },
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = theme.keyFunctionalTextColor,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

/**
 * One-tap "paste screenshot" chip shown in the suggestion strip right after an image was copied or a
 * screenshot was taken. Mirrors Gboard's clipboard-image suggestion.
 */
@Composable
fun PendingImageChip(
    item: ClipboardItem,
    theme: KeyboardTheme,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp, horizontal = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.accentPrimary.copy(alpha = 0.16f))
            .border(1.dp, theme.accentPrimary.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable { onIntent(KeyboardIntent.PasteClipboardImage(item.id)) }
            .padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ClipboardImageThumbnail(item = item, size = 30.dp, theme = theme)
        Text(
            text = "Paste image",
            color = theme.accentPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onIntent(KeyboardIntent.DismissPendingImage(item.id)) }
                .semantics { contentDescription = "Dismiss image suggestion" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = theme.keyFunctionalTextColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
