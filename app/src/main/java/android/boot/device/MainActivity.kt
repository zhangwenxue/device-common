package android.boot.device

import android.boot.ble.common.permission.BLEPermission
import android.boot.device.ecg.facade.ECGSdk
import android.boot.device.ui.theme.DevicecommonTheme
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.experimental.and


class MainActivity : ComponentActivity() {
    private val bleScope = BLEPermission(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DevicecommonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Demo(modifier = Modifier.padding(innerPadding), bleScope)
                }
            }
        }
    }
}


@Composable
fun Demo(modifier: Modifier = Modifier, bleScope: BLEPermission) {

    var ecgDevice: String by remember {
        mutableStateOf("--")
    }

    var dataCollection: String by remember {
        mutableStateOf("")
    }


    var sn: String by remember {
        mutableStateOf("")
    }

    var writeSN: String by remember {
        mutableStateOf("")
    }

    var version: String by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var number by remember {
        mutableIntStateOf(-1)
    }
    var errorCount by remember {
        mutableIntStateOf(-1)
    }

    var data by remember {
        mutableStateOf((listOf<String>()))
    }
    LazyColumn(modifier = modifier) {
        item { Text(text = ecgDevice) }
        item { Text(text = "SN:$sn") }
        item { Text(text = "WriteSN:$writeSN") }
        item { Text(text = "Version:$version") }
        item {
            Button(onClick = {
                ecgDevice = "--"
                scope.launch {
                    ECGSdk.getDevice(true, bleScope).onSuccess {
                        ecgDevice = it.name

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Device found:${it.name}", Toast.LENGTH_LONG
                            ).show()
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "No device found:${it.message}", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }) {
                Text(text = "Discover")
            }
        }

        item {
            Button(onClick = {
                scope.launch {
                    if (ECGSdk.isListening) {
                        ECGSdk.stopListen()
                        return@launch
                    }
                    data = emptyList()
                    number = -1
                    errorCount = 0
                    ECGSdk.listen().onEach {
                        it.onSuccess { array ->
                            val list = array.toList().chunked(31)
                            list.forEach { b ->
                                val no = (b[4] and 0xFF.toByte()).toInt()
                                if (number != -1 && ((number + 1).mod(64)) != no.mod(64)) {
                                    errorCount = errorCount++
                                }
                                number = no
                            }

                        }
                    }.flowOn(Dispatchers.Default).collect {
                        it.onSuccess { d ->
                            data = data.toMutableList().apply {
                                add(d.joinToString { i -> "%02x".format(i) })
                                toList()
                            }
                        }.onFailure {
                            dataCollection = it.message ?: ""
                        }
                    }
                }
            }) {
                Text(text = "collect")
            }
        }

        item {
            Button(onClick = {
                scope.launch {
                    ECGSdk.readSN().onSuccess { sn = it }.onFailure { sn = it.message ?: "error" }
                }
            }) {
                Text(text = "read sn")
            }
        }

        item {
            Button(onClick = {
                scope.launch {
                    ECGSdk.readVersion().onSuccess { version = it }
                        .onFailure { version = it.message ?: "error" }
                }
            }) {
                Text(text = "read version")
            }
        }

        item {
            Button(onClick = {
                scope.launch {
                    ECGSdk.writeSn(/*"2024-${(0..10).random()}"*/"C122023${(1000..9999).random()}000000")
                        .onSuccess { writeSN = "Write success" }
                        .onFailure { writeSN = it.message ?: "error" }
                }
            }) {
                Text(text = "write sn")
            }
        }

        item { Text(text = "error count:$number") }
        item { Text(text = dataCollection) }
        itemsIndexed(data) { a, b ->
            val color = if (a.mod(2) == 0) Color.Gray else Color.White
            Text(
                text = b,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color)
                    .padding(vertical = 2.dp),
                color = Color.Black
            )
        }
    }
}

