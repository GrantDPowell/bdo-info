package com.gpowell.bdoboss.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.ui.theme.BdoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.log2
import kotlin.math.roundToInt

private const val MAX_Z = 5                       // deepest bundled tile level
private const val TILE = 256f
private val WORLD = TILE * (1 shl MAX_Z)          // 8192 — map size in "world" px

/**
 * Native pan/pinch-zoom tile map of the BDO world. Tiles (z/x/y JPEGs, z1–5) are bundled in
 * assets and rendered to a Canvas: the right zoom level is picked from the current scale and
 * only visible tiles are decoded (cached, off the main thread). Fully offline.
 */
@Composable
fun MapScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val loading = remember { mutableSetOf<String>() }

    var scale by remember { mutableFloatStateOf(0f) }      // screen px per world px
    var trans by remember { mutableStateOf(Offset.Zero) }  // screen pos of world (0,0)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0A0D12))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val fit = minOf(wPx, hPx) / WORLD
        // initialise to fit + center on first layout
        if (scale == 0f && wPx > 0f) {
            scale = fit
            trans = Offset((wPx - WORLD * fit) / 2f, (hPx - WORLD * fit) / 2f)
        }
        val minScale = fit
        val maxScale = 1.6f

        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(minScale, maxScale)
                    trans = (trans - centroid) * (ns / scale) + centroid + pan
                    scale = ns
                    // keep the map from drifting entirely off-screen
                    val mapPx = WORLD * ns
                    val minX = wPx - mapPx; val minY = hPx - mapPx
                    trans = Offset(
                        trans.x.coerceIn(minOf(0f, minX), maxOf(0f, minX)),
                        trans.y.coerceIn(minOf(0f, minY), maxOf(0f, minY)),
                    )
                }
            },
        ) {
            if (scale <= 0f) return@Canvas
            val tz = log2((WORLD * scale) / TILE).roundToInt().coerceIn(1, MAX_Z)
            val tiles = 1 shl tz
            val tileWorld = WORLD / tiles
            val tileScreen = tileWorld * scale

            fun visRange(originScreen: Float, dim: Float): IntRange {
                val a = ((-originScreen) / scale / tileWorld).toInt()
                val b = ((dim - originScreen) / scale / tileWorld).toInt()
                return a.coerceIn(0, tiles - 1)..b.coerceIn(0, tiles - 1)
            }
            for (tx in visRange(trans.x, size.width)) {
                for (ty in visRange(trans.y, size.height)) {
                    val key = "$tz/$tx/$ty"
                    val sx = tx * tileWorld * scale + trans.x
                    val sy = ty * tileWorld * scale + trans.y
                    val bmp = cache[key]
                    if (bmp != null) {
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                            dstSize = IntSize(tileScreen.roundToInt() + 1, tileScreen.roundToInt() + 1),
                        )
                    } else {
                        drawRect(Color(0xFF11151C), topLeft = Offset(sx, sy), size = Size(tileScreen, tileScreen))
                        if (loading.add(key)) {
                            scope.launch(Dispatchers.IO) {
                                val b = runCatching {
                                    ctx.assets.open("map/$key.jpg").use { BitmapFactory.decodeStream(it) }
                                }.getOrNull()?.asImageBitmap()
                                if (b != null) cache[key] = b
                                loading.remove(key)
                            }
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC0A0D12)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BdoColors.goldHi)
        }
    }
}
