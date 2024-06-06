package android.boot.device

import android.boot.ble.common.permission.BleScope
import android.boot.device.api.Device
import android.boot.device.api.DeviceDiscovery
import android.boot.device.ecg.ble.BleDevice3GenFilter
import android.boot.device.ecg.ble.BleDeviceDiscovery
import android.boot.device.ui.theme.DevicecommonTheme
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val bleScope = BleScope(this)
    private val deviceDiscovery: DeviceDiscovery<BluetoothDevice> = BleDeviceDiscovery()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val flow: Flow<Result<List<Device<BluetoothDevice>>>> = deviceDiscovery.deviceFlow
        bleScope.withBle(onFeatureUnavailable = {
            Toast.makeText(this@MainActivity, "设备无蓝牙", Toast.LENGTH_SHORT).show()
        }) {
            deviceDiscovery.discover(
                scope = lifecycleScope,
                timeoutMills = 5000,
                BleDevice3GenFilter()
            )
        }
        setContent {
            DevicecommonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        flow = flow,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
    flow: Flow<Result<List<Device<BluetoothDevice>>>>
) {
    val result = flow.collectAsState(initial = Result.success(emptyList()))
    var device: Device<BluetoothDevice>? by remember {
        mutableStateOf(null)
    }
    var text by remember {
        mutableStateOf("")
    }
    val scope = rememberCoroutineScope()
    result.value.onSuccess {
        LazyColumn {
            items(it) {
                Text(text = "${it.name}(${it.mac})", modifier = Modifier.clickable {
                    device = it
//                    cmdBuf = new byte[5];
//                    cmdBuf[0] = PACKET_HEAD;
//                    cmdBuf[1] = 0x09;
//                    cmdBuf[2] = 0x00;
//                    cmdBuf[3] = 0x09;
//                    cmdBuf[4] = PACKET_TAIL;
                    scope.launch {
                        it.write(byteArrayOf(0xA5.toByte(), 0x09, 0x00, 0x09, 0x5A), 100)
                            .onSuccess {
                                text = "write success"
                            }.onFailure { text = "write failed:${it.message}" }
                    }
                  /*  scope.launch {
                        device?.listen()?.collect {
                            it.onSuccess { text = it.joinToString { "%02x".format(it) } }
                                .onFailure {
                                    text = it.message ?: ""
                                }
                        }
                    }*/
                })
            }
            item {
                Text(text = text)
            }
        }
    }.onFailure {
        Text(text = "Failed:${it.message}")
    }

    DisposableEffect(Unit) {
        onDispose {
            device?.close()
        }
    }
}

