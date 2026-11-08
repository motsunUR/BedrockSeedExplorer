package jp.bedrockseedexplorer

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
                                    BitmapFactory.decodeByteArray(fetchBytes(mapUrl), 0, fetchBytes(mapUrl).size)
                                }
                                val found = loadStructures(seedValue, centerX, centerZ)
                                mapBitmap = bitmap
                                structures = found
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("バイオームマップ", style = MaterialTheme.typography.titleMedium)
                    val bitmap = mapBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Bedrock biome map",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.FillBounds
                        )
                        Text("表示範囲: 中心座標から約±800ブロック（16ブロック間隔）")
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
