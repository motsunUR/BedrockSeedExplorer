package jp.bedrockseedexplorer

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
    connection.setRequestProperty("User-Agent", "BedrockSeedExplorer/0.2")
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
    val url = "https://www.mineseedfinder.com/GetStructures/bedrock?seed=$seed&x=$x&z=$z&radius=100&max=100&types=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,23,24"
    val json = JSONObject(String(fetchBytes(url), Charsets.UTF_8))
    val results = json.optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
        val item = results.optJSONObject(i) ?: return@mapNotNull null
        Structure(item.optString("name", "structure"), item.optInt("x"), item.optInt("z"), item.optDouble("distance"))
    }
}

@Composable
private fun SeedExplorerScreen() {
    var seed by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("0") }
    var z by remember { mutableStateOf("0") }
    var mapBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var structures by remember { mutableStateOf<List<Structure>>(emptyList()) }
    var message by remember { mutableStateOf("Seed値と中心座標を入力してマップを表示できます。") }
    var loading by remember { mutableStateOf(false) }
    var mapScale by remember { mutableFloatStateOf(1f) }
    var mapOffsetX by remember { mutableFloatStateOf(0f) }
    var mapOffsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    val imageBytes = fetchBytes(mapUrl)
                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        ?: throw IllegalStateException("マップ画像を読み込めませんでした")
                                }
                                val found = loadStructures(seedValue, centerX, centerZ)
                                mapBitmap = bitmap
                                structures = found
                                mapScale = 1f
                                mapOffsetX = 0f
                                mapOffsetY = 0f
                                message = "バイオームマップを取得しました。構造物: ${found.size}件"
                            } catch (e: Exception) {
                                message = "取得に失敗しました: ${e.message ?: "通信エラー"}"
                            } finally {
                                loading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "読み込み中…" else "マップを表示") }

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("バイオームマップ", style = MaterialTheme.typography.titleMedium)
                    val bitmap = mapBitmap
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(340.dp)
                                .background(Color(0xFF20252B), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF59636F), RoundedCornerShape(10.dp))
                                .pointerInput(bitmap) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        mapScale = (mapScale * zoom).coerceIn(1f, 6f)
                                        mapOffsetX += pan.x
                                        mapOffsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Bedrock biome map",
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = mapScale
                                    scaleY = mapScale
                                    translationX = mapOffsetX
                                    translationY = mapOffsetY
                                },
                                contentScale = ContentScale.FillBounds
                            )
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                drawLine(Color.White.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(cx - 12f, cy), androidx.compose.ui.geometry.Offset(cx + 12f, cy), 2f)
                                drawLine(Color.White.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(cx, cy - 12f), androidx.compose.ui.geometry.Offset(cx, cy + 12f), 2f)
                            }
                            Text("中心", modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(5.dp)).padding(6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Text("${(mapScale * 100).toInt()}%", modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(5.dp)).padding(6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { mapScale = (mapScale / 1.25f).coerceAtLeast(1f); if (mapScale == 1f) { mapOffsetX = 0f; mapOffsetY = 0f } }, modifier = Modifier.weight(1f)) { Text("−") }
                            OutlinedButton(onClick = { mapScale = (mapScale * 1.25f).coerceAtMost(6f) }, modifier = Modifier.weight(1f)) { Text("＋") }
                            OutlinedButton(onClick = { mapScale = 1f; mapOffsetX = 0f; mapOffsetY = 0f }, modifier = Modifier.weight(1f)) { Text("リセット") }
                        }
                        Text("Seed: $seed", style = MaterialTheme.typography.bodySmall)
                        Text("中心座標  X: $x  /  Z: $z", style = MaterialTheme.typography.bodyMedium)
                        Text("チャンク座標  X: ${(x.toIntOrNull() ?: 0).floorDiv(16)}  /  Z: ${(z.toIntOrNull() ?: 0).floorDiv(16)}", style = MaterialTheme.typography.bodySmall)
                        Text("ピンチで拡大・縮小、ドラッグで移動できます。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Seedを入力して「マップを表示」を押してください。")
                    }
                }
            }

            Text("近くの構造物", style = MaterialTheme.typography.titleLarge)
            if (structures.isEmpty()) {
                Text("構造物データはまだありません。マップを読み込むと検索します。")
            } else {
                structures.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.name.replace('_', ' ')) },
                        supportingContent = { Text("X: ${item.x} / Z: ${item.z} ・ 距離: ${"%.1f".format(item.distance)}チャンク") }
                    )
                }
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text("Powered by MineSeedFinder. 非公式アプリ。Minecraft公式とは関係ありません。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
