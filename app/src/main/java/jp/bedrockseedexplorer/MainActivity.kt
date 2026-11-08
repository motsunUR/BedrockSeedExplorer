package jp.bedrockseedexplorer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAP_SAMPLES = 100
private const val MAP_STEP = 16
private const val MAP_BLOCKS = MAP_SAMPLES * MAP_STEP
private const val STRUCTURE_RADIUS = 80

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SeedExplorerScreen() } }
    }
}

private data class Structure(val name: String, val x: Int, val z: Int, val distance: Double)

private suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000
    c.readTimeout = 25000
    c.setRequestProperty("User-Agent", "BedrockSeedExplorer/0.4")
    try {
        val code = c.responseCode
        if (code !in 200..299) {
            val retry = c.getHeaderField("Retry-After")
            throw IllegalStateException(if (code == 429) "API利用制限中です。" + (retry ?: "しばらく") + "秒待ってください。" else "HTTP " + code)
        }
        c.inputStream.use { it.readBytes() }
    } finally { c.disconnect() }
}

private suspend fun fetchMap(seed: Long, x: Int, z: Int): Bitmap {
    val s = URLEncoder.encode(seed.toString(), "UTF-8")
    val url = "https://www.mineseedfinder.com/GetBiomes?seed=" + s + "&x=" + x + "&z=" + z + "&size=" + MAP_SAMPLES + "&scale=" + MAP_STEP + "&outputformat=image"
    val bytes = fetchBytes(url)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("地形画像を読み込めませんでした")
}

private suspend fun fetchStructures(seed: Long, x: Int, z: Int): List<Structure> {
    val s = URLEncoder.encode(seed.toString(), "UTF-8")
    val url = "https://www.mineseedfinder.com/GetStructures/bedrock?seed=" + s + "&x=" + x + "&z=" + z + "&radius=" + STRUCTURE_RADIUS + "&max=200&types=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,23,24"
    val array = JSONObject(String(fetchBytes(url), Charsets.UTF_8)).optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        Structure(o.optString("name", "structure"), o.optInt("x"), o.optInt("z"), o.optDouble("distance"))
    }
}

private suspend fun fetchBiome(seed: Long, x: Int, z: Int): String {
    val s = URLEncoder.encode(seed.toString(), "UTF-8")
    return String(fetchBytes("https://www.mineseedfinder.com/GetBiomeAt?seed=" + s + "&x=" + x + "&z=" + z), Charsets.UTF_8).trim()
}

private fun structureIcon(name: String): String {
    val n = name.lowercase().replace("_", "").replace(" ", "")
    return when {
        "village" in n -> "🏘️"
        "temple" in n -> "🛕"
        "witchhut" in n -> "🛖"
        "igloo" in n -> "🧊"
        "oceanruin" in n -> "⚓"
        "shipwreck" in n -> "🚢"
        "monument" in n -> "🏛️"
        "mansion" in n -> "🏰"
        "outpost" in n -> "🗼"
        "portal" in n -> "🌀"
        "ancientcity" in n -> "🏚️"
        "treasure" in n -> "💎"
        "mineshaft" in n -> "⛏️"
        "trailruin" in n -> "🏺"
        "trialchamber" in n -> "⚔️"
        else -> "📍"
    }
}

@Composable
private fun MapViewport(
    bitmap: Bitmap, structures: List<Structure>, centerX: Int, centerZ: Int,
    showGrid: Boolean, showStructures: Boolean, scale: Float, offsetX: Float, offsetY: Float,
    onTransform: (Float, Offset) -> Unit, onTap: (Int, Int) -> Unit, onEdgePan: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val s by rememberUpdatedState(scale)
    val ox by rememberUpdatedState(offsetX)
    val oy by rememberUpdatedState(offsetY)
    val cx by rememberUpdatedState(centerX)
    val cz by rememberUpdatedState(centerZ)
    val transform by rememberUpdatedState(onTransform)
    val tapCallback by rememberUpdatedState(onTap)
    val edgeCallback by rememberUpdatedState(onEdgePan)
    Box(
        modifier.background(Color(0xFF20252B), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF59636F), RoundedCornerShape(10.dp))
            .onSizeChanged { viewport = it }
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (s * zoom).coerceIn(1f, 12f)
                    val nextX = ox + pan.x
                    val nextY = oy + pan.y
                    transform(zoom, pan)
                    val w = viewport.width.toFloat()
                    val h = viewport.height.toFloat()
                    if (w > 0 && h > 0 && (abs(nextX) > w * .42f || abs(nextY) > h * .42f)) {
                        edgeCallback(
                            cx - (nextX / (w * nextScale) * MAP_BLOCKS).roundToInt(),
                            cz - (nextY / (h * nextScale) * MAP_BLOCKS).roundToInt()
                        )
                    }
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures { p ->
                    val w = viewport.width.toFloat()
                    val h = viewport.height.toFloat()
                    if (w > 0 && h > 0) {
                        tapCallback(
                            cx + (((p.x - w / 2f - ox) / (w * s)) * MAP_BLOCKS).roundToInt(),
                            cz + (((p.y - h / 2f - oy) / (h * s)) * MAP_BLOCKS).roundToInt()
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(bitmap.asImageBitmap(), "Bedrock biome map",
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
            }, contentScale = ContentScale.FillBounds)
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
        }) {
            val w = size.width
            val h = size.height
            if (showGrid) {
                for (i in 0..25) {
                    val px = i * w / 25f
                    val py = i * h / 25f
                    val major = i % 4 == 0
                    val color = Color.White.copy(alpha = if (major) .42f else .13f)
                    drawLine(color, Offset(px, 0f), Offset(px, h), if (major) 1.1f else .5f)
                    drawLine(color, Offset(0f, py), Offset(w, py), if (major) 1.1f else .5f)
                }
            }
            if (showStructures) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = 30f
                    setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
                }
                structures.forEach { st ->
                    val dx = st.x - centerX
                    val dz = st.z - centerZ
                    if (abs(dx) <= MAP_BLOCKS / 2 && abs(dz) <= MAP_BLOCKS / 2) {
                        val px = (dx.toFloat() / MAP_BLOCKS + .5f) * w
                        val py = (dz.toFloat() / MAP_BLOCKS + .5f) * h
                        drawCircle(Color(0xFFFFD54F), 12f, Offset(px, py))
                        drawContext.canvas.nativeCanvas.drawText(structureIcon(st.name), px, py + 10f, paint)
                    }
                }
            }
            drawLine(Color.White, Offset(w / 2 - 10f, h / 2), Offset(w / 2 + 10f, h / 2), 2f)
            drawLine(Color.White, Offset(w / 2, h / 2 - 10f), Offset(w / 2, h / 2 + 10f), 2f)
            drawCircle(Color.White, 3f, Offset(w / 2, h / 2), style = Stroke(1.5f))
        }
        Text("中心 X:" + centerX + "  Z:" + centerZ, Modifier.align(Alignment.TopStart).padding(8.dp)
            .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(5.dp)).padding(6.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
        Text((scale * 100).toInt().toString() + "%", Modifier.align(Alignment.BottomEnd).padding(8.dp)
            .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(5.dp)).padding(6.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SeedExplorerScreen() {
    var seed by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("0") }
    var z by remember { mutableStateOf("0") }
    var centerX by remember { mutableIntStateOf(0) }
    var centerZ by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var structures by remember { mutableStateOf<List<Structure>>(emptyList()) }
    var message by remember { mutableStateOf("Seed値を入力してマップを表示してください。") }
    var loading by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showGrid by remember { mutableStateOf(true) }
    var showStructures by remember { mutableStateOf(true) }
    var fullScreen by remember { mutableStateOf(false) }
    var selectedX by remember { mutableStateOf<Int?>(null) }
    var selectedZ by remember { mutableStateOf<Int?>(null) }
    var biome by remember { mutableStateOf("地図をタップすると座標とバイオームを表示") }
    var biomeLoading by remember { mutableStateOf(false) }
    var biomeJob by remember { mutableStateOf<Job?>(null) }
    var lastMapRequest by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    fun resetMap() { scale = 1f; offsetX = 0f; offsetY = 0f }

    fun loadAt(seedValue: Long, newX: Int, newZ: Int) {
        if (loading) return
        loading = true
        message = "地図の続きを読み込んでいます…"
        scope.launch {
            try {
                val wait = (10_500L - (System.currentTimeMillis() - lastMapRequest)).coerceAtLeast(0L)
                if (lastMapRequest != 0L && wait > 0) delay(wait)
                lastMapRequest = System.currentTimeMillis()
                val nextBitmap = fetchMap(seedValue, newX, newZ)
                val nextStructures = fetchStructures(seedValue, newX, newZ)
                bitmap = nextBitmap
                structures = nextStructures
                centerX = newX; centerZ = newZ
                x = newX.toString(); z = newZ.toString()
                offsetX = 0f; offsetY = 0f
                message = "地図を更新しました。構造物 " + nextStructures.size + " 件"
            } catch (e: Exception) {
                message = "読み込み失敗: " + (e.message ?: "通信エラー")
            } finally { loading = false }
        }
    }

    fun tapMap(worldX: Int, worldZ: Int) {
        selectedX = worldX; selectedZ = worldZ
        biome = "バイオームを確認中…"
        val seedValue = seed.trim().toLongOrNull() ?: return
        biomeJob?.cancel()
        biomeJob = scope.launch {
            delay(450)
            biomeLoading = true
            try {
                biome = fetchBiome(seedValue, worldX, worldZ).removePrefix("minecraft:").replace('_', ' ')
            } catch (e: Exception) {
                biome = e.message ?: "取得できませんでした"
            } finally { biomeLoading = false }
        }
    }

    fun edgePan(newX: Int, newZ: Int) {
        val seedValue = seed.trim().toLongOrNull() ?: return
        if (!loading) loadAt(seedValue, newX, newZ)
    }

    if (fullScreen && bitmap != null) {
        Dialog(onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seed Map", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { fullScreen = false }) { Text("閉じる") }
                }
                MapViewport(bitmap!!, structures, centerX, centerZ, showGrid, showStructures,
                    scale, offsetX, offsetY,
                    { zoom, pan -> scale = (scale * zoom).coerceIn(1f, 12f); offsetX += pan.x; offsetY += pan.y },
                    ::tapMap, ::edgePan, Modifier.weight(1f).fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ scale = (scale / 1.25f).coerceAtLeast(1f) }, Modifier.weight(1f)) { Text("−") }
                    OutlinedButton({ scale = (scale * 1.25f).coerceAtMost(12f) }, Modifier.weight(1f)) { Text("＋") }
                    OutlinedButton(::resetMap, Modifier.weight(1f)) { Text("リセット") }
                }
                Text("タップ地点: X " + (selectedX?.toString() ?: "—") + " / Z " + (selectedZ?.toString() ?: "—") + " ・ " + biome, color = Color.White)
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bedrock Seed Explorer", style = MaterialTheme.typography.headlineSmall)
            Text("Minecraft 統合版専用", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(seed, { seed = it }, label = { Text("Seed値") },
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(x, { x = it }, label = { Text("中心 X") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(z, { z = it }, label = { Text("中心 Z") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Button(enabled = !loading, onClick = {
                val seedValue = seed.trim().toLongOrNull()
                val cx = x.toIntOrNull(); val cz = z.toIntOrNull()
                if (seedValue == null || cx == null || cz == null) message = "Seed値・座標を整数で入力してください。"
                else {
                    selectedX = null; selectedZ = null
                    biome = "地図をタップすると座標とバイオームを表示"
                    loadAt(seedValue, cx, cz)
                }
            }, Modifier.fillMaxWidth()) { Text(if (loading) "読み込み中…" else "マップを表示") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("バイオーム Seed Map", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        if (bitmap != null) TextButton({ fullScreen = true }) { Text("全画面") }
                    }
                    val current = bitmap
                    if (current == null) Text("Seedを入力してマップを表示してください。")
                    else {
                        MapViewport(current, structures, centerX, centerZ, showGrid, showStructures,
                            scale, offsetX, offsetY,
                            { zoom, pan -> scale = (scale * zoom).coerceIn(1f, 12f); offsetX += pan.x; offsetY += pan.y },
                            ::tapMap, ::edgePan, Modifier.fillMaxWidth().height(360.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(showGrid, { showGrid = !showGrid }, label = { Text("チャンクグリッド") })
                            FilterChip(showStructures, { showStructures = !showStructures }, label = { Text("建物アイコン") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton({ scale = (scale / 1.25f).coerceAtLeast(1f) }, Modifier.weight(1f)) { Text("−") }
                            OutlinedButton({ scale = (scale * 1.25f).coerceAtMost(12f) }, Modifier.weight(1f)) { Text("＋") }
                            OutlinedButton(::resetMap, Modifier.weight(1f)) { Text("リセット") }
                        }
                        Text("Seed: " + seed + " ・ 中心 X:" + centerX + " / Z:" + centerZ)
                        Text("チャンク X:" + centerX.floorDiv(16) + " / Z:" + centerZ.floorDiv(16))
                        Text("選択座標: X " + (selectedX?.toString() ?: "—") + " / Z " + (selectedZ?.toString() ?: "—"), style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("バイオーム: " + biome, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            if (biomeLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Text("ドラッグで移動・ピンチで拡大縮小。表示範囲の端まで移動すると次の地形を自動取得。タップで座標とバイオームを確認。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("近くの構造物", style = MaterialTheme.typography.titleLarge)
            if (structures.isEmpty()) Text("構造物データはまだありません。")
            else structures.sortedBy { it.distance }.forEach { item ->
                ListItem(
                    headlineContent = { Text(structureIcon(item.name) + "  " + item.name.replace('_', ' ')) },
                    supportingContent = { Text("X:" + item.x + " / Z:" + item.z + " ・ 距離:" + String.format("%.1f", item.distance) + "チャンク") },
                    trailingContent = { TextButton({ tapMap(item.x, item.z) }) { Text("座標") } }
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text("地形・バイオーム・構造物データ: MineSeedFinder API。地形APIの利用制限に合わせ、範囲の自動更新には約10.5秒の間隔を設けています。", style = MaterialTheme.typography.bodySmall)
            Text("非公式アプリ。Minecraft公式とは関係ありません。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
