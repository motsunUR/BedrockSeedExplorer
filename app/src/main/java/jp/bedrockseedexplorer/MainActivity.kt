package jp.bedrockseedexplorer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SeedExplorerScreen() } }
    }
}

private data class Structure(val name: String, val x: Int, val z: Int, val distance: Double)

private suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 20000
    connection.setRequestProperty("User-Agent", "BedrockSeedExplorer/0.3")
    try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("サーバーエラー: HTTP ${connection.responseCode}")
        }
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

private suspend fun loadStructures(seed: Long, x: Int, z: Int): List<Structure> {
    val url = "https://www.mineseedfinder.com/GetStructures/bedrock?seed=$seed&x=$x&z=$z&radius=100&max=200&types=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,23,24"
    val json = JSONObject(String(fetchBytes(url), Charsets.UTF_8))
    val results = json.optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
        val item = results.optJSONObject(i) ?: return@mapNotNull null
        Structure(item.optString("name", "structure"), item.optInt("x"), item.optInt("z"), item.optDouble("distance"))
    }
}

@Composable
private fun MapViewport(
    bitmap: Bitmap,
    structures: List<Structure>,
    centerX: Int,
    centerZ: Int,
    showGrid: Boolean,
    showStructures: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF20252B), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF59636F), RoundedCornerShape(10.dp))
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan) }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "統合版バイオームマップ",
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offsetX; translationY = offsetY
            },
            contentScale = ContentScale.FillBounds
        )
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offsetX; translationY = offsetY
        }) {
            val w = size.width
            val h = size.height
            if (showGrid) {
                // GetBiomes の1サンプルは16ブロック間隔。4チャンクごとに主グリッドを表示。
                val step = w / 25f
                for (i in 0..25) {
                    val p = i * step
                    drawLine(Color.White.copy(alpha = if (i % 5 == 0) 0.55f else 0.20f), Offset(p, 0f), Offset(p, h), if (i % 5 == 0) 1.2f else 0.5f)
                    drawLine(Color.White.copy(alpha = if (i % 5 == 0) 0.55f else 0.20f), Offset(0f, p), Offset(w, p), if (i % 5 == 0) 1.2f else 0.5f)
                }
            }
            if (showStructures) {
                // 画像は中心から東西南北へ約800ブロック（50チャンク）の範囲。
                structures.forEach { s ->
                    val dx = s.x - centerX
                    val dz = s.z - centerZ
                    if (abs(dx) <= 800 && abs(dz) <= 800) {
                        val px = (dx + 800f) / 1600f * w
                        val py = (dz + 800f) / 1600f * h
                        drawCircle(Color(0xFFFFD54F), 5.5f, Offset(px, py))
                        drawCircle(Color(0xFF20252B), 5.5f, Offset(px, py), style = Stroke(1.5f))
                    }
                }
            }
            drawLine(Color.White, Offset(w / 2f - 10f, h / 2f), Offset(w / 2f + 10f, h / 2f), 2f)
            drawLine(Color.White, Offset(w / 2f, h / 2f - 10f), Offset(w / 2f, h / 2f + 10f), 2f)
            drawCircle(Color.White, 3f, Offset(w / 2f, h / 2f), style = Stroke(1.5f))
        }
        Text(
            "中心 X:$centerX  Z:$centerZ",
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(5.dp)).padding(6.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall
        )
        Text(
            "${(scale * 100).toInt()}%",
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(5.dp)).padding(6.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SeedExplorerScreen() {
    var seed by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("0") }
    var z by remember { mutableStateOf("0") }
    var mapBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var structures by remember { mutableStateOf<List<Structure>>(emptyList()) }
    var message by remember { mutableStateOf("Seed値と中心座標を入力してマップを表示できます。") }
    var loading by remember { mutableStateOf(false) }
    var mapScale by remember { mutableFloatStateOf(1f) }
    var mapOffsetX by remember { mutableFloatStateOf(0f) }
    var mapOffsetY by remember { mutableFloatStateOf(0f) }
    var showGrid by remember { mutableStateOf(true) }
    var showStructures by remember { mutableStateOf(true) }
    var fullScreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun transform(zoom: Float, pan: Offset) {
        mapScale = (mapScale * zoom).coerceIn(1f, 8f)
        mapOffsetX += pan.x
        mapOffsetY += pan.y
    }
    fun resetMap() {
        mapScale = 1f; mapOffsetX = 0f; mapOffsetY = 0f
    }

    if (fullScreen && mapBitmap != null) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seed Map", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { fullScreen = false }) { Text("閉じる") }
                }
                MapViewport(
                    bitmap = mapBitmap!!, structures = structures,
                    centerX = x.toIntOrNull() ?: 0, centerZ = z.toIntOrNull() ?: 0,
                    showGrid = showGrid, showStructures = showStructures,
                    scale = mapScale, offsetX = mapOffsetX, offsetY = mapOffsetY,
                    onTransform = ::transform, modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { mapScale = (mapScale / 1.25f).coerceAtLeast(1f) }, modifier = Modifier.weight(1f)) { Text("−") }
                    OutlinedButton(onClick = { mapScale = (mapScale * 1.25f).coerceAtMost(8f) }, modifier = Modifier.weight(1f)) { Text("＋") }
                    OutlinedButton(onClick = ::resetMap, modifier = Modifier.weight(1f)) { Text("リセット") }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Bedrock Seed Explorer", style = MaterialTheme.typography.headlineSmall)
            Text("Minecraft 統合版専用", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = seed, onValueChange = { seed = it },
                label = { Text("Seed値") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = x, onValueChange = { x = it }, label = { Text("中心 X") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = z, onValueChange = { z = it }, label = { Text("中心 Z") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Button(
                enabled = !loading,
                onClick = {
                    val seedValue = seed.trim().toLongOrNull()
                    val centerX = x.toIntOrNull()
                    val centerZ = z.toIntOrNull()
                    if (seedValue == null || centerX == null || centerZ == null) {
                        message = "Seed値・座標を正しい整数で入力してください。"
                    } else {
                        loading = true
                        message = "マップデータを取得しています…"
                        scope.launch {
                            try {
                                val encodedSeed = URLEncoder.encode(seedValue.toString(), "UTF-8")
                                val mapUrl = "https://www.mineseedfinder.com/GetBiomes?seed=$encodedSeed&x=$centerX&z=$centerZ&size=100&scale=16&outputformat=image"
                                val bitmap = withContext(Dispatchers.IO) {
                                    val bytes = fetchBytes(mapUrl)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        ?: throw IllegalStateException("マップ画像を読み込めませんでした")
                                }
                                val found = loadStructures(seedValue, centerX, centerZ)
                                mapBitmap = bitmap
                                structures = found
                                resetMap()
                                message = "マップを取得しました。構造物: ${found.size}件"
                            } catch (e: Exception) {
                                message = "取得に失敗しました: ${e.message ?: "通信エラー"}"
                            } finally { loading = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "読み込み中…" else "マップを表示") }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("バイオーム Seed Map", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        if (mapBitmap != null) TextButton(onClick = { fullScreen = true }) { Text("全画面") }
                    }
                    val bitmap = mapBitmap
                    if (bitmap != null) {
                        MapViewport(
                            bitmap = bitmap, structures = structures,
                            centerX = x.toIntOrNull() ?: 0, centerZ = z.toIntOrNull() ?: 0,
                            showGrid = showGrid, showStructures = showStructures,
                            scale = mapScale, offsetX = mapOffsetX, offsetY = mapOffsetY,
                            onTransform = ::transform, modifier = Modifier.fillMaxWidth().height(320.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = showGrid, onClick = { showGrid = !showGrid }, label = { Text("チャンクグリッド") })
                            FilterChip(selected = showStructures, onClick = { showStructures = !showStructures }, label = { Text("構造物") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { mapScale = (mapScale / 1.25f).coerceAtLeast(1f) }, modifier = Modifier.weight(1f)) { Text("−") }
                            OutlinedButton(onClick = { mapScale = (mapScale * 1.25f).coerceAtMost(8f) }, modifier = Modifier.weight(1f)) { Text("＋") }
                            OutlinedButton(onClick = ::resetMap, modifier = Modifier.weight(1f)) { Text("リセット") }
                        }
                        Text("Seed: $seed", style = MaterialTheme.typography.bodySmall)
                        Text("中心 X: $x / Z: $z ・ チャンク X: ${(x.toIntOrNull() ?: 0).floorDiv(16)} / Z: ${(z.toIntOrNull() ?: 0).floorDiv(16)}")
                        Text("ピンチで拡大・縮小、ドラッグで移動。黄色の点が構造物です。", style = MaterialTheme.typography.bodySmall)
                    } else Text("Seedを入力して「マップを表示」を押してください。")
                }
            }

            Text("近くの構造物", style = MaterialTheme.typography.titleLarge)
            if (structures.isEmpty()) Text("構造物データはまだありません。")
            else structures.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.name.replace('_', ' ')) },
                    supportingContent = { Text("X: ${item.x} / Z: ${item.z} ・ 距離: ${"%.1f".format(item.distance)}チャンク") }
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text("地形データ・構造物予測: MineSeedFinder。Bedrock 1.18以降の対応範囲。予測結果は実際のワールドと異なる場合があります。", style = MaterialTheme.typography.bodySmall)
            Text("非公式アプリ。Minecraft公式とは関係ありません。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
