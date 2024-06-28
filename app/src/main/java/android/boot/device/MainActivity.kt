package android.boot.device

import android.boot.ble.common.permission.BLEPermission
import android.boot.device.ecg.facade.ECGSdk
import android.boot.device.ui.theme.DevicecommonTheme
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = ECGSdk.statusFlow.collectAsState()
    Column(modifier = modifier) {
        Text(text = ecgDevice)
        Text(text = "State:${state.value}")
        Button(onClick = {
            ecgDevice = "--"
            scope.launch {
                ECGSdk.setup(true, bleScope).onSuccess {
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
        Button(onClick = {
            scope.launch {
                if (ECGSdk.isListening) {
                    ECGSdk.stopListen()
                    return@launch
                }
                ECGSdk.listen().collect {
                    it.onSuccess { data ->
                        dataCollection = data.joinToString { i -> "%02x".format(i) }
                    }.onFailure {
                        dataCollection = it.message ?: ""
                    }
                }
            }
        }) {
            Text(text = "collect")
        }

        Text(text = dataCollection)
    }
}

