package jp.bedrockseedexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SeedExplorerScreen() } }
    }
}

@Composable
private fun SeedExplorerScreen() {
    var seed by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("0") }
    var z by remember { mutableStateOf("0") }
    var message by remember { mutableStateOf("Seed値と中心座標を入力してください。") }
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
            Button(onClick = {
                message = if (seed.toLongOrNull() == null) {
                    "Seed値は整数で入力してください。"
                } else if (x.toIntOrNull() == null || z.toIntOrNull() == null) {
                    "中心座標は整数で入力してください。"
                } else {
                    "入力を受け付けました。地形・バイオーム・構造物データの連携は現在開発中です。"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("マップを表示") }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("マップ", style = MaterialTheme.typography.titleMedium)
                    Text("地形データ未接続", style = MaterialTheme.typography.headlineSmall)
                    Text("現在はSeed入力画面の開発段階です。正確なBedrock地形を表示するには、対応する地形生成データ/APIの実装が必要です。")
                }
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text("※非公式アプリ。Minecraft公式とは関係ありません。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
