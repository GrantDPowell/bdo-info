package com.gpowell.bdoboss.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.R
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoChip
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.roundToInt

private const val MAX_Z = 5
private const val TILE = 256f
private val WORLD = TILE * (1 shl MAX_Z)   // 8192

@Serializable
private data class MapNode(
    val n: String = "", val t: String = "", val nx: Float = 0f, val ny: Float = 0f,
    val c: String = "", val cp: String = "",
)

private val LAYERS = listOf("city", "village", "gateway", "trade", "office", "danger", "connect")
private val LAYER_LABEL = mapOf(
    "city" to "Cities", "village" to "Villages", "gateway" to "Gateways", "trade" to "Trade",
    "office" to "Offices", "danger" to "Danger", "connect" to "Connections",
)

@Composable
fun MapScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val loading = remember { mutableSetOf<String>() }
    val measurer = rememberTextMeasurer()

    // node-type icons (decoded once)
    val icons = remember {
        fun ic(id: Int) = BitmapFactory.decodeResource(ctx.resources, id).asImageBitmap()
        mapOf(
            "city" to ic(R.drawable.node_city), "village" to ic(R.drawable.node_village),
            "gateway" to ic(R.drawable.node_gateway), "trade" to ic(R.drawable.node_trade),
            "office" to ic(R.drawable.node_office), "danger" to ic(R.drawable.node_danger),
            "connect" to ic(R.drawable.node_connect),
        )
    }

    var scale by remember { mutableFloatStateOf(0f) }      // screen px per world px (NOT read in composition)
    var trans by remember { mutableStateOf(Offset.Zero) }
    var nodes by remember { mutableStateOf<List<MapNode>>(emptyList()) }
    var selected by remember { mutableStateOf<MapNode?>(null) }
    var enabled by remember { mutableStateOf(setOf("city", "village", "gateway", "trade", "office", "danger")) }
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        nodes = withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("map/nodes.json").bufferedReader().use {
                    Json { ignoreUnknownKeys = true }.decodeFromString<List<MapNode>>(it.readText())
                }
            }.getOrDefault(emptyList())
        }
        // preload low-zoom base tiles so the fallback is always available
        withContext(Dispatchers.IO) {
            for (z in 1..3) { val n = 1 shl z; for (x in 0 until n) for (y in 0 until n) loadTile(ctx, cache, loading, "$z/$x/$y") }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0A0D12))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val fit = minOf(wPx, hPx) / WORLD

        // init OUTSIDE composition reads of scale/trans (so gestures don't recompose this tree)
        LaunchedEffect(wPx, hPx) {
            if (wPx > 0f && scale == 0f) {
                scale = fit
                trans = Offset((wPx - WORLD * fit) / 2f, (hPx - WORLD * fit) / 2f)
            }
        }

        fun clampTrans(t: Offset, s: Float): Offset {
            val mapPx = WORLD * s
            val minX = wPx - mapPx; val minY = hPx - mapPx
            return Offset(t.x.coerceIn(minOf(0f, minX), maxOf(0f, minX)), t.y.coerceIn(minOf(0f, minY), maxOf(0f, minY)))
        }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (scale <= 0f) return@detectTransformGestures
                        val ns = (scale * zoom).coerceIn(fit, 1.6f)
                        trans = clampTrans((trans - centroid) * (ns / scale) + centroid + pan, ns)
                        scale = ns
                    }
                }
                .pointerInput(nodes, enabled) {
                    detectTapGestures { tap ->
                        if (scale <= 0f) return@detectTapGestures
                        var best: MapNode? = null; var bestD = 28f
                        nodes.forEach { node ->
                            if (node.t !in enabled) return@forEach
                            val px = node.nx * WORLD * scale + trans.x
                            val py = node.ny * WORLD * scale + trans.y
                            val d = hypot(px - tap.x, py - tap.y)
                            if (d < bestD) { bestD = d; best = node }
                        }
                        selected = best
                    }
                },
        ) {
            if (scale <= 0f) return@Canvas
            val tz = log2((WORLD * scale) / TILE).roundToInt().coerceIn(1, MAX_Z)
            val tiles = 1 shl tz
            val tileWorld = WORLD / tiles

            fun range(origin: Float, dim: Float): IntRange {
                val a = ((-origin) / scale / tileWorld).toInt()
                val b = ((dim - origin) / scale / tileWorld).toInt()
                return a.coerceIn(0, tiles - 1)..b.coerceIn(0, tiles - 1)
            }
            for (tx in range(trans.x, size.width)) {
                for (ty in range(trans.y, size.height)) {
                    val sx = (tx * tileWorld * scale + trans.x)
                    val sy = (ty * tileWorld * scale + trans.y)
                    val sw = tileWorld * scale
                    drawBestTile(ctx, cache, loading, scope, tz, tx, ty, sx, sy, sw)
                }
            }

            // markers (filtered by enabled layers, viewport-culled)
            val labelZoom = tz >= 4
            nodes.forEach { node ->
                if (node.t !in enabled) return@forEach
                val px = node.nx * WORLD * scale + trans.x
                val py = node.ny * WORLD * scale + trans.y
                if (px < -16f || py < -16f || px > size.width + 16f || py > size.height + 16f) return@forEach
                val sel = node == selected
                val sz = when (node.t) { "city" -> 26f; "connect" -> 14f; else -> 20f } * (if (sel) 1.35f else 1f)
                icons[node.t]?.let { ic ->
                    drawImage(ic, dstOffset = IntOffset((px - sz / 2).roundToInt(), (py - sz / 2).roundToInt()), dstSize = IntSize(sz.roundToInt(), sz.roundToInt()))
                }
                if (node.n.isNotEmpty() && (node.t == "city" || sel || (node.t != "connect" && labelZoom))) {
                    val style = TextStyle(
                        color = if (sel) BdoColors.goldHi else Color.White,
                        fontSize = if (node.t == "city") 12.sp else 10.sp,
                        fontWeight = if (node.t == "city") FontWeight.Bold else FontWeight.Medium,
                        shadow = Shadow(Color.Black, blurRadius = 6f),
                    )
                    val tl = measurer.measure(node.n, style)
                    drawText(tl, topLeft = Offset(px - tl.size.width / 2f, py - sz / 2 - tl.size.height - 1f))
                }
            }
        }

        // ── top: back + search + layers ──
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MapBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(8.dp))
                if (showSearch) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f).height(52.dp),
                        singleLine = true, placeholder = { Text("Search node…") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xCC0A0D12), unfocusedContainerColor = Color(0xCC0A0D12),
                            focusedBorderColor = BdoColors.gold, cursorColor = BdoColors.gold,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    MapBtn(Icons.Filled.Close, "Close") { showSearch = false; query = "" }
                } else {
                    Spacer(Modifier.weight(1f))
                    MapBtn(Icons.Filled.Search, "Search") { showSearch = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LAYERS.forEach { layer ->
                    BdoChip(
                        LAYER_LABEL[layer] ?: layer, active = layer in enabled,
                        onClick = { enabled = if (layer in enabled) enabled - layer else enabled + layer },
                    )
                }
            }
        }

        // search results
        if (showSearch && query.length >= 2) {
            val q = query.trim()
            val results = nodes.filter { it.n.contains(q, true) && it.n.isNotEmpty() }.distinctBy { it.n }.take(20)
            Box(Modifier.align(Alignment.TopCenter).padding(top = 116.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                BdoCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    LazyColumn(Modifier.height(if (results.isEmpty()) 48.dp else minOf(results.size * 44, 300).dp)) {
                        items(results, key = { it.n + it.t }) { node ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .pointerInput(node) { detectTapGestures {
                                        scale = 1.0f
                                        trans = clampTrans(Offset(wPx / 2 - node.nx * WORLD, hPx / 2 - node.ny * WORLD), 1.0f)
                                        selected = node; showSearch = false; query = ""
                                    } }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(node.n, Modifier.weight(1f), color = BdoColors.onBg, fontWeight = FontWeight.SemiBold)
                                Text(LAYER_LABEL[node.t] ?: node.t, style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                            }
                        }
                    }
                }
            }
        }

        // ── bottom: detail card ──
        selected?.let { node ->
            BdoCard(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                facet = true, glow = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(node.n.ifBlank { LAYER_LABEL[node.t] ?: node.t }, style = BdoType.display.copy(fontSize = 20.sp), color = BdoColors.onBg)
                        val meta = buildList {
                            add(LAYER_LABEL[node.t]?.trimEnd('s') ?: node.t)
                            if (node.c.isNotBlank()) add(node.c)
                            if (node.cp.isNotBlank() && node.cp != "0") add("${node.cp} CP")
                        }.joinToString(" · ")
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = BdoColors.goldHi)
                    }
                    IconButton(onClick = { selected = null }) { Icon(Icons.Filled.Close, "Close", tint = BdoColors.onFaint) }
                }
            }
        }
    }
}

@Composable
private fun MapBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC0A0D12))) {
        Icon(icon, desc, tint = BdoColors.goldHi)
    }
}

/** Draw the best available tile for (tz,x,y): the exact one if cached, else an ancestor's
 *  sub-region scaled up (keeps the map visible while zooming → no blank flicker). */
private fun DrawScope.drawBestTile(
    ctx: android.content.Context, cache: SnapshotStateMap<String, ImageBitmap>, loading: MutableSet<String>,
    scope: kotlinx.coroutines.CoroutineScope, tz: Int, x: Int, y: Int, sx: Float, sy: Float, sw: Float,
) {
    val exact = cache["$tz/$x/$y"]
    val dstO = IntOffset(sx.roundToInt(), sy.roundToInt())
    val dstS = IntSize(sw.roundToInt() + 1, sw.roundToInt() + 1)
    if (exact != null) {
        drawImage(exact, dstOffset = dstO, dstSize = dstS)
        return
    }
    // request the exact tile
    loadTile(ctx, cache, loading, "$tz/$x/$y", scope)
    // fallback to an ancestor
    var pz = tz - 1
    while (pz >= 1) {
        val factor = 1 shl (tz - pz)
        val parent = cache["$pz/${x / factor}/${y / factor}"]
        if (parent != null) {
            val sub = 256 / factor
            drawImage(
                parent,
                srcOffset = IntOffset((x % factor) * sub, (y % factor) * sub), srcSize = IntSize(sub, sub),
                dstOffset = dstO, dstSize = dstS,
            )
            return
        }
        pz--
    }
    drawRect(Color(0xFF11151C), topLeft = Offset(sx, sy), size = Size(sw, sw))
}

private fun loadTile(
    ctx: android.content.Context, cache: SnapshotStateMap<String, ImageBitmap>, loading: MutableSet<String>,
    key: String, scope: kotlinx.coroutines.CoroutineScope? = null,
) {
    if (cache.containsKey(key) || !loading.add(key)) return
    val run = Runnable {
        val b = runCatching { ctx.assets.open("map/$key.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()?.asImageBitmap()
        if (b != null) cache[key] = b
        loading.remove(key)
    }
    if (scope != null) scope.launch(Dispatchers.IO) { run.run() } else run.run()
}
