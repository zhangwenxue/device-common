package android.boot.device.ecg.facade

import android.boot.device.api.DeviceLog
import android.boot.device.api.ECGDevice
import android.boot.device.api.State
import android.boot.device.ecg.nordicble.BleDevice3GenFilter
import android.boot.device.ecg.nordicble.NordicBleDiscovery
import android.boot.device.ecg.usb.UsbDeviceDiscovery
import android.boot.device.ecg.usb.UsbDeviceFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DeviceAgent {
    private val scope by lazy {
        CoroutineScope(Dispatchers.Default)
    }

    private var _statusFlow: StateFlow<State> = MutableStateFlow(State.Idle)
    val statusFlow = _statusFlow

    private var _dataFlow: StateFlow<Result<ByteArray>> = MutableStateFlow(
        Result.success(
            byteArrayOf()
        )
    )
    val dataFlow = _dataFlow

    private val lock = Mutex()

    private val usbDeviceDiscovery = UsbDeviceDiscovery()
    private val bleDeviceDiscovery = NordicBleDiscovery()

    private var targetDevice: ECGDevice? = null

    @Volatile
    var isCollecting: Boolean = false

    suspend fun firstEcgDevice(): Result<ECGDevice> {
        return lock.withLock {
            val cache = targetDevice
            if (cache != null) {
                DeviceLog.log("<Agent> Discover cached device:$cache")
                Result.success(cache)
            } else {
                val usbEcgFlow =
                    usbDeviceDiscovery.discover(scope, 2000L, UsbDeviceFilter())
                val bleEcgFlow = bleDeviceDiscovery.discover(scope, 5000L, BleDevice3GenFilter())

                /**
                coroutineScope {
                val usbDeferred = async { usbEcgFlow.first() }
                val bleDeferred = async { bleEcgFlow.first() }
                runCatching {
                val usbRet = usbDeferred.await()
                if (usbRet.isSuccess) {
                bleDeferred.cancel()
                return@coroutineScope usbRet.getOrNull()?.firstOrNull()
                ?.let { Result.success(it) }
                ?: Result.failure<ECGDevice>(Throwable("No portable usb device found"))
                }
                val bleRet = bleDeferred.await()
                return@coroutineScope if (bleRet.isFailure) Result.failure<ECGDevice>(
                bleRet.exceptionOrNull() ?: Throwable("No device found")
                ) else bleRet.getOrNull()?.firstOrNull()?.let { Result.success(it) }
                ?: Result.failure<ECGDevice>(Throwable("No portable ble device found"))
                }
                }*/
                coroutineScope {
                    val result = CompletableDeferred<Result<ECGDevice>>()

                    launch {
                        val ecgDeviceRet = bleEcgFlow.firstOrNull() // 收集flow2的所有数据
                        if (!result.isCompleted) {
                            val ret = ecgDeviceRet?.let {
                                if (it.isFailure) Result.failure(
                                    it.exceptionOrNull() ?: Throwable("No Ble ecg found!")
                                ) else {
                                    it.getOrNull()?.firstOrNull()?.let { device ->
                                        Result.success(device)
                                    } ?: Result.failure(Throwable("No ble ecg found!"))
                                }
                            } ?: Result.failure(Throwable("No ble ecg found!"))
                            result.complete(ret)
                        }
                    }

                    launch {
                        usbEcgFlow.collect {
                            val device = it.getOrNull()?.firstOrNull()
                            if (it.isSuccess && device != null) {
                                result.complete(Result.success(device))
                                this@coroutineScope.cancel() // 取消整个作用域
                            }
                        }
                    }
                    result.await()
                }
            }
        }
    }

    fun stopDiscover() {
        usbDeviceDiscovery.stop()
        bleDeviceDiscovery.stop()
    }

    fun connect(ecgDevice: ECGDevice) {
        targetDevice = ecgDevice
        scope.launch {
            _statusFlow = ecgDevice.eventFlow.stateIn(scope)
        }
    }

    fun disconnect() {
        targetDevice?.run {
            stopDiscover()
            this.close()
        }
        targetDevice = null
    }

    fun collect() {
        scope.launch {
            if (isCollecting) return@launch
            isCollecting = targetDevice != null
            targetDevice?.listen()?.let {
                _dataFlow = it.stateIn(scope)
            }
        }

    }

    fun stopCollect() {
        scope.launch {
            if (!isCollecting) return@launch
            targetDevice?.run {
                stopListen()
                isCollecting = false
            }
        }
    }

    suspend fun readSn(): Result<String> {
        return Result.success("NO_SN")
    }

    suspend fun writeSn(sn: String): Result<Unit> {
        return Result.success(Unit)
    }

}
