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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.ui.theme.BdoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.log2
import kotlin.math.roundToInt

private const val MAX_Z = 5                       // deepest bundled tile level
private const val TILE = 256f
private val WORLD = TILE * (1 shl MAX_Z)          // 8192 — map size in "world" px

/** A map marker (node/town) in normalized Web-Mercator coords [0,1]. */
@Serializable
private data class MapNode(val n: String = "", val t: String = "", val nx: Float = 0f, val ny: Float = 0f)

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
    val measurer = rememberTextMeasurer()

    var scale by remember { mutableFloatStateOf(0f) }      // screen px per world px
    var trans by remember { mutableStateOf(Offset.Zero) }  // screen pos of world (0,0)
    var nodes by remember { mutableStateOf<List<MapNode>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        nodes = withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("map/nodes.json").bufferedReader().use {
                    Json { ignoreUnknownKeys = true }.decodeFromString<List<MapNode>>(it.readText())
                }
            }.getOrDefault(emptyList())
        }
    }

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

            // ── markers: towns / nodes ──
            val labelZoom = tz >= 4          // only show labels once zoomed in (avoids clutter)
            nodes.forEach { node ->
                val px = node.nx * WORLD * scale + trans.x
                val py = node.ny * WORLD * scale + trans.y
                if (px < -20f || py < -20f || px > size.width + 20f || py > size.height + 20f) return@forEach
                val (color, radius, important) = when (node.t) {
                    "city" -> Triple(BdoColors.goldHi, 6f, true)
                    "village" -> Triple(Color(0xFF7FD4FF), 5f, true)
                    "gateway" -> Triple(Color(0xFFB8B8C8), 4f, true)
                    "trade" -> Triple(BdoColors.up, 4f, false)
                    "office" -> Triple(Color(0xFFB07CF0), 4f, false)
                    "danger" -> Triple(BdoColors.down, 4f, false)
                    else -> Triple(BdoColors.gold.copy(alpha = 0.7f), 2.6f, false)  // connect
                }
                drawCircle(Color.Black.copy(alpha = 0.5f), radius + 1.5f, Offset(px, py))
                drawCircle(color, radius, Offset(px, py))
                if (node.n.isNotEmpty() && (node.t == "city" || (important && labelZoom))) {
                    val style = TextStyle(
                        color = Color.White, fontSize = if (node.t == "city") 12.sp else 10.sp,
                        fontWeight = if (node.t == "city") FontWeight.Bold else FontWeight.Medium,
                        shadow = Shadow(Color.Black, blurRadius = 6f),
                    )
                    val tl = measurer.measure(node.n, style)
                    drawText(tl, topLeft = Offset(px - tl.size.width / 2f, py - radius - tl.size.height - 2f))
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
