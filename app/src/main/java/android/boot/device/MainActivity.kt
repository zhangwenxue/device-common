package android.boot.device

import android.boot.ble.common.permission.BleScope
import android.boot.device.api.Device
import android.boot.device.api.DeviceDiscovery
import android.boot.device.ecg.ble.BleDeviceDiscovery
import android.boot.device.ui.theme.DevicecommonTheme
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : ComponentActivity() {
    private val bleScope = BleScope(this)
    private val deviceDiscovery: DeviceDiscovery<BluetoothDevice> = BleDeviceDiscovery()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        /* val flow: Flow<Result<List<Device<BluetoothDevice>>>> = deviceDiscovery.deviceFlow
         bleScope.withBle(onFeatureUnavailable = {
             Toast.makeText(this@MainActivity, "设备无蓝牙", Toast.LENGTH_SHORT).show()
         }) {
             deviceDiscovery.discover(scope = lifecycleScope)
         }*/
        setContent {
            DevicecommonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var text: String? by remember {
                        mutableStateOf(null)
                    }

                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        text = Settings.System.getString(context.contentResolver, "rw.sn")
                    }


                    Column {
                        Text(text = "sn:$text", modifier = Modifier.padding(innerPadding))
                        Button(onClick = {
                            Settings.System.putString(
                                context.contentResolver,
                                "rw.sn",
                                SimpleDateFormat("yyyyMMdd:HH:mm:ss").format(Date())
                            )
                            text = Settings.Global.getString(context.contentResolver, "rw.sn")
                        }) {
                            Text(text = "Set New Sn")
                        }
                    }
                    /*Greeting(
                        name = "Android",
                        flow = flow,
                        modifier = Modifier.padding(innerPadding)
                    )*/
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

    result.value.onSuccess {
        LazyColumn {
            items(it) {
                Text(text = "${it.name}(${it.mac}))")
            }
        }
    }

    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}