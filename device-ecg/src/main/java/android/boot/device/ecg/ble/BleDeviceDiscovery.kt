package android.boot.device.ecg.ble

import android.annotation.SuppressLint
import android.boot.common.provider.globalContext
import android.boot.device.api.Device
import android.boot.device.api.DeviceDiscovery
import android.boot.device.api.DeviceFilter
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.ScanFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class BleDevice3GenFilter(
    override val name: String = "WWKECG12E",
    override val nameMask: String = ""
) : DeviceFilter

class BleDeviceDiscovery : DeviceDiscovery<BluetoothDevice> {
    private val bluetoothLe by lazy {
        BluetoothLe(globalContext)
    }

    private val innerScope = CoroutineScope(Dispatchers.Default)
    private var scanResultList = listOf<Device<BluetoothDevice>>()

    @Volatile
    private var scanJobs: Job? = null

    private val _scanResultFlow = MutableStateFlow<Result<List<Device<BluetoothDevice>>>>(
        Result.success(
            emptyList()
        )
    )

    @OptIn(FlowPreview::class)
    @SuppressLint("MissingPermission")
    override fun discover(
        scope: CoroutineScope?,
        timeoutMills: Long,
        vararg deviceFilters: DeviceFilter
    ) {
        val workScope = scope ?: innerScope

        scanResultList = emptyList()
        scanJobs?.cancel()


        val scanFilters = deviceFilters.map { ScanFilter(deviceName = it.name) }

        if (scanFilters.isEmpty()) {
            workScope.launch {
                _scanResultFlow.emit(
                    Result.failure(Throwable("请指定设备名称进行扫描"))
                )
            }
            return
        }


        val job = workScope.launch {
            bluetoothLe.scan(scanFilters)
                .timeout(if (timeoutMills > 0) timeoutMills.milliseconds else Duration.INFINITE)
                .catch {
                    if (it is TimeoutCancellationException) {
                        if (scanResultList.isEmpty()) {
                            _scanResultFlow.emit(
                                Result.failure(Throwable("No device found in $timeoutMills ms"))
                            )
                        }
                    } else {
                        throw it
                    }
                }
                .stateIn(
                    scope = workScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = null
                ).collect { result ->
                    result?.run {
                        BleDeviceFactory.createBleDevice(device, deviceAddress.address)
                            ?.run {
                                val device = this
                                val idx =
                                    scanResultList.map { it.mac }.indexOf(device.mac)
                                if (idx >= 0) {
                                    scanResultList = scanResultList.toMutableList().apply {
                                        this[idx] = device
                                    }.toList()
                                } else {
                                    scanResultList = scanResultList.toMutableList().apply {
                                        add(device)
                                    }.toList()
                                }
                                _scanResultFlow.emit(
                                    Result.success(scanResultList)
                                )
                            }
                    }

                }
        }
        scanJobs = job
    }

    override val deviceFlow: Flow<Result<List<Device<BluetoothDevice>>>>
        get() = _scanResultFlow


    override fun cancel(): Result<Unit> {
        scanJobs?.cancel()
        return Result.success(Unit)
    }

}