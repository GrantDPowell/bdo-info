package com.gpowell.bdoboss.ui.market

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.gpowell.bdoboss.data.market.ItemIconResolver

/**
 * Real Central Market item icon for [itemId], resolved lazily from bdocodex via
 * [ItemIconResolver]. Falls back to the grade-colored monogram while resolving,
 * on a resolve miss, or if the image fails to load — so every row always shows
 * *something*. Because it's used inside LazyColumn/LazyVerticalGrid, only visible
 * rows trigger a lookup, and the resolver caches across the session.
 */
@Composable
internal fun ItemIcon(itemId: Int, name: String, grade: Int, size: Dp = 36.dp) {
    var url by remember(itemId) { mutableStateOf(ItemIconResolver.cached(itemId)) }
    LaunchedEffect(itemId, name) {
        url = if (ItemIconResolver.isResolved(itemId)) {
            ItemIconResolver.cached(itemId)
        } else {
            ItemIconResolver.resolve(itemId, name)
        }
    }

    val resolved = url
    if (resolved == null) {
        GradeMonogram(name = name, grade = grade, size = size)
    } else {
        SubcomposeAsyncImage(
            model = resolved,
            contentDescription = name,
            modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Fit,
            loading = { GradeMonogram(name = name, grade = grade, size = size) },
            error = { GradeMonogram(name = name, grade = grade, size = size) },
        )
    }
}
