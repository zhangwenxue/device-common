package android.boot.device.ecg.facade

import android.boot.ble.common.permission.BLEPermission
import android.boot.common.extensions.i
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object ECGSdk {
    private val scope by lazy {
        CoroutineScope(Dispatchers.Default)
    }

    private var _statusFlow: StateFlow<State> = MutableStateFlow(State.Idle)
    val statusFlow = _statusFlow

    private val lock = Mutex()

    private val usbDeviceDiscovery = UsbDeviceDiscovery()
    private val bleDeviceDiscovery = NordicBleDiscovery()

    private var targetDevice: ECGDevice? = null

    @Volatile
    var isListening: Boolean = false

    suspend fun setup(force: Boolean = false, bleScope: BLEPermission? = null): Result<ECGDevice> {
        bleDeviceDiscovery.bleScope = bleScope
        return lock.withLock {
            if (force) {
                targetDevice?.close()
                targetDevice = null
            }
            val cache = targetDevice
            if (cache != null) {
                DeviceLog.log("<Agent> Discover cached device:$cache")
                Result.success(cache)
            } else {
                val usbEcgFlow =
                    usbDeviceDiscovery.discover(scope, 2000L, UsbDeviceFilter())
                val bleEcgFlow = bleDeviceDiscovery.discover(scope, 5000L, BleDevice3GenFilter())
                var usbDiscovered = false
                var bleResult: Result<ECGDevice>? = null
                coroutineScope {
                    val result = CompletableDeferred<Result<ECGDevice>>()
                    val bleDiscoveryJob = launch {
                        val ecgDeviceRet = bleEcgFlow.firstOrNull()
                        DeviceLog.log(
                            "<Agent> ble device collected:${
                                ecgDeviceRet?.getOrNull()?.firstOrNull()?.name
                            },result.isCompleted:${result.isCompleted}"
                        )
                        val ret = ecgDeviceRet?.let {
                            if (it.isFailure) Result.failure(
                                it.exceptionOrNull() ?: Throwable("No Ble ecg found!")
                            ) else {
                                it.getOrNull()?.firstOrNull()?.let { device ->
                                    Result.success(device)
                                } ?: Result.failure(Throwable("No ble ecg found!"))
                            }
                        } ?: Result.failure(Throwable("No ble ecg found!"))
                        if (usbDiscovered) {
                            if (!result.isCompleted) {
                                result.complete(ret)
                            }
                        } else {
                            bleResult = ret
                        }

                    }

                    launch {
                        usbEcgFlow.collect {
                            val device = it.getOrNull()?.firstOrNull()
                            DeviceLog.log("<Agent> usb device collected:${device?.name}")
                            usbDiscovered = true
                            if (it.isSuccess && device != null) {
                                result.complete(Result.success(device))
                                DeviceLog.i("<Agent> complete discover job")
                                bleDiscoveryJob.cancel()
                            } else {
                                bleResult?.let { ret -> result.complete(ret) }
                            }
                        }
                    }
                    result.await().also {
                        it.onSuccess { device ->
                            targetDevice = device
                            DeviceLog.log("<Agent> ECG(${device.name}) is good to go!")
                        }.onFailure {
                            DeviceLog.log("<Agent> NO DEVICE FOUND!")
                        }
                    }
                }
            }
        }
    }

    suspend fun listen(autoDiscover: Boolean = true): Flow<Result<ByteArray>> {
        DeviceLog.log("<Agent> Start listen,checking device...")
        val deviceRet = checkDevice(autoDiscover)
        if (deviceRet.isFailure) return flowOf<Result<ByteArray>>(Result.failure(Throwable("No ECG device found"))).also {
            DeviceLog.log("<Agent> listen failed,no ECG hw found!")
        }
        isListening = true
        return deviceRet.getOrThrow().listen()
    }

    suspend fun readSN(autoDiscover: Boolean = true, autoClose: Boolean = false): Result<String> {
        val deviceRet = checkDevice(autoDiscover)
        if (deviceRet.isFailure) return Result.failure(Throwable("No ECG device found"))
        return deviceRet.getOrThrow().readSN(autoClose)
    }


    suspend fun readVersion(
        autoDiscover: Boolean = true,
        autoClose: Boolean = false
    ): Result<String> {
        val deviceRet = checkDevice(autoDiscover)
        if (deviceRet.isFailure) return Result.failure(Throwable("No ECG device found"))
        return deviceRet.getOrThrow().readVersion(autoClose)
    }

    suspend fun writeSn(
        sn: String,
        autoDiscover: Boolean = true,
        autoClose: Boolean = false
    ): Result<Unit> {
        val deviceRet = checkDevice(autoDiscover)
        if (deviceRet.isFailure) return Result.failure(Throwable("No ECG device found"))
        return deviceRet.getOrThrow().writeSN(sn, autoClose)
    }

    fun disconnect() {
        targetDevice?.run {
            this.close()
        }
        targetDevice = null
    }

    suspend fun stopListen() = lock.withLock {
        withContext(Dispatchers.Default) {
            if (!isListening) return@withContext
            targetDevice?.run {
                stopListen().onFailure { DeviceLog.log("<Agent> Stop listen failed:${it.message}") }
                    .onSuccess { DeviceLog.log("<Agent> Stop listen success") }
                isListening = false
            }
        }
    }

    private suspend fun checkDevice(autoDiscover: Boolean = false): Result<ECGDevice> {
        val device = targetDevice
        return if (device != null) {
            Result.success(device)
        } else {
            if (!autoDiscover) {
                Result.failure<ECGDevice>(Throwable("You must setup an ECG device first"))
                    .also {
                        DeviceLog.log("<Agent> autoDiscover disabled.So,you need to invoke setUp() first")
                    }
            } else {
                DeviceLog.log("<Agent> Setup ECG device automatically...")
                setup()
            }
        }
    }
}
