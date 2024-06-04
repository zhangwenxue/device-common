package android.boot.device.ecg.ble

import android.Manifest
import android.annotation.SuppressLint
import android.boot.common.provider.globalContext
import android.boot.device.api.Device
import android.boot.device.api.DeviceDiscovery
import android.boot.device.api.DeviceFilter
import android.content.pm.PackageManager
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.ScanFilter
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val BLE_DEVICE_2GEN = "WWKECG12E"

data class BleDevice2GenFilter(
    override val name: String = "WWKECG12E",
    override val nameMask: String = ""
) : DeviceFilter

class BleDeviceDiscovery : DeviceDiscovery<BluetoothDevice> {
    private val bluetoothLe by lazy {
        BluetoothLe(globalContext)
    }

    private val bleDevice2GenFilter = BleDevice2GenFilter()
    private val innerScope = CoroutineScope(Dispatchers.Default)
    private var scanResultList = listOf<Device<BluetoothDevice>>()

    @Volatile
    private var scanJobs: Job? = null

    private val _scanResultFlow = MutableStateFlow<Result<List<Device<BluetoothDevice>>>>(
        Result.success(
            emptyList()
        )
    )
    val scanState: MutableStateFlow<Result<List<Device<BluetoothDevice>>>> = _scanResultFlow

    @SuppressLint("MissingPermission")
    override fun discover(
        vararg deviceFilters: DeviceFilter,
        scope: CoroutineScope?,
        timeoutMills: Long,
    ) {
        val workScope = scope ?: innerScope
        if (ActivityCompat.checkSelfPermission(
                globalContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            workScope.launch {
                _scanResultFlow.emit(Result.failure(Throwable("android.permission.BLUETOOTH_SCAN permission is not granted!")))
            }
            return
        }
        scanResultList = emptyList()
        scanJobs?.cancel()


        val scanFilters = deviceFilters.map { ScanFilter(deviceName = it.name) }

        /*if (scanFilters.isEmpty()) {
            workScope.launch {
                _scanResultFlow.emit(
                    Result.failure(Throwable("请指定设备名称进行扫描"))
                )
            }
            return
        }*/


        val job = workScope.launch {
            bluetoothLe.scan(scanFilters).stateIn(
                scope = workScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = null
            ).collect { result ->
                result?.run {
                    BleDeviceFactory.createBleDevice(device, deviceAddress.address)
                        ?.run {
                            val device = this
                            val idx =
                                scanResultList.indexOfFirst { result.deviceAddress.address == device.mac }
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