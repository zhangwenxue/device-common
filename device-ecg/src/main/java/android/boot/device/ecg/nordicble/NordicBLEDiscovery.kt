package android.boot.device.ecg.nordicble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.boot.ble.common.permission.BLEPermission
import android.boot.common.provider.globalContext
import android.boot.device.api.DeviceDiscovery
import android.boot.device.api.DeviceFilter
import android.boot.device.api.DeviceLog
import android.boot.device.api.ECGDevice
import android.boot.device.api.Transmission
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.timeout
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanFilter
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


data class BleDevice3GenFilter(
    override val name: String = "WWKECG12E",
    override val nameMask: String = "",
    override val transmission: Transmission = Transmission.Ble

) : DeviceFilter

class NordicBleDiscovery(var bleScope: BLEPermission? = null) : DeviceDiscovery {

    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    @SuppressLint("MissingPermission")
    @OptIn(FlowPreview::class)
    override fun discover(
        scope: CoroutineScope?,
        timeoutMills: Long,
        vararg deviceFilters: DeviceFilter
    ): Flow<Result<List<ECGDevice>>> {

        val aggregator = BleScanResultAggregator()

        val scanFilters = deviceFilters.map { BleScanFilter(deviceName = it.name) }

        return flow {
            if (scanFilters.isEmpty()) {
                emit(Result.failure(Throwable("请指定设备名称进行扫描")))
                return@flow
            }
            DeviceLog.log("<BLE> Start ecg discover")
            val bluetoothReady = ifBluetoothReady()
            if (bleScope == null && (!bluetoothReady)) {
                emit(Result.failure(Throwable("Please ensure bluetooth permissions are all granted!")))
                return@flow
            }

            suspend fun scan() {
                var savedList = listOf<ECGDevice>()

                BleScanner(globalContext)
                    .scan(scanFilters)
                    .timeout(if (timeoutMills > 0) timeoutMills.milliseconds else Duration.INFINITE)
                    .filter {
                        scanFilters.mapNotNull { filter -> filter.deviceName }
                            .contains(it.device.name)
                    }
                    .catch {
                        if (it is TimeoutCancellationException) {
                            if (savedList.isEmpty()) {
                                DeviceLog.log("<BLE> ecg discover timeout:$timeoutMills ms")
                                emit(Result.failure(Throwable("No device found in $timeoutMills ms")))
                            }
                        } else {
                            DeviceLog.log("<BLE> ecg discover exception", throwable = it)
                            emit(Result.failure(it))
                        }
                    }
                    .map { aggregator.aggregateDevices(it) }
                    .onEach {
                        savedList = it.map { device ->
                            NordicBleEcg3G(
                                device,
                                device.address,
                                device.name ?: ""
                            )
                        }
                        DeviceLog.log("<BLE> discovered:${savedList.joinToString { ecgDevice -> ecgDevice.name }} ")
                        emit(Result.success(savedList))
                    }.collect()
            }


            if (ifBluetoothReady()) {
                scan()
            } else {
                bleScope?.let {
                    val permissionRet = obtainBlePermission(it).first()
                    permissionRet.onSuccess {
                        scan()
                    }.onFailure {
                        emit(
                            Result.failure(
                                permissionRet.exceptionOrNull()
                                    ?: Throwable("Bluetooth permission not granted")
                            )
                        )
                    }
                }
                    ?: emit(Result.failure(Throwable("Please ensure bluetooth permissions are all granted!")))
            }
        }
    }


    override fun stop(): Result<Unit> {
        return Result.success(Unit)
    }


    private fun obtainBlePermission(bleScope: BLEPermission): Flow<Result<Unit>> = callbackFlow {
        bleScope.withBLE(
            onFeatureUnavailable = { trySendBlocking(Result.failure(Throwable("Bluetooth unsupported"))) },
            onBleDisabled = { trySendBlocking(Result.failure(Throwable("Bluetooth disabled"))) },
            onPermissionDenied = { trySendBlocking(Result.failure(Throwable("Bluetooth permission denied"))) },
        ) {
            trySendBlocking(Result.success(Unit))
        }
        awaitClose { cancel() }
    }

    private fun ifBluetoothReady() =
        blePermissions.all { globalContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } && isBleEnabled()

    private fun isBleEnabled(): Boolean {
        val bluetoothManager =
            globalContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (bluetoothManager == null || adapter == null) {
            return false
        }

        return (adapter.isEnabled)
    }

}