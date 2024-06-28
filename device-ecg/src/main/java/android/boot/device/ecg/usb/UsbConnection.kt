package android.boot.device.ecg.usb

import android.boot.common.provider.globalContext
import android.boot.device.api.Channel
import android.boot.device.api.Connection
import android.boot.device.api.State
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UsbCdcChannel(
    override val id: String,
    override val name: String?,
    private val usbDevice: UsbDevice,
    private val eventFlow: MutableStateFlow<State>
) : Channel {
    private val usbManager by lazy {
        globalContext.getSystemService(UsbManager::class.java)
    }
    private val driver by lazy {
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .firstOrNull { it.device == usbDevice }
    }

    private var cdcTransfer: UsbCdcTransfer? = null

    private val mutex = Mutex()

    suspend fun connect(): Result<Unit> = mutex.withLock {
        if (cdcTransfer != null) {
            eventFlow.update { State.Connected }
            return Result.success(Unit)
        }
        eventFlow.update { State.Connecting }
        val connection = usbManager.openDevice(usbDevice)
            ?: return Result.failure<Unit>(Throwable("Failed to create usb connection")).also {
                eventFlow.update { State.Disconnected }
            }
        val port = driver?.ports?.firstOrNull()
            ?: return Result.failure<Unit>(Throwable("Failed to find driver port")).also {
                eventFlow.update { State.Disconnected }
            }
        cdcTransfer = UsbCdcTransfer(connection, port)
        val result = cdcTransfer?.openDevice()?.onFailure {
            cdcTransfer = null
            eventFlow.update { State.Disconnected }
        }?.onSuccess {
            eventFlow.update { State.Connected }
        } ?: Result.failure<Unit>(Throwable("CdcTransfer not initialized")).also {
            eventFlow.update { State.Disconnected }
        }
        return result
    }

    override suspend fun read(src: ByteArray?, timeoutMillis: Int): Result<ByteArray> {
        return Result.success(byteArrayOf())
    }

    override suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit> {
        cdcTransfer?.write(dest)
        return Result.success(Unit)
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        cdcTransfer?.startEcgCollect(byteArrayOf(0xA5.toByte(), 0x03, 0x00, 0x03, 0x5A))
            ?.onFailure {
                return flowOf(Result.failure(Throwable("Device Closed")))
            }
        return callbackFlow {
            cdcTransfer?.listener = object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray?) {
                    data?.run { trySend(Result.success(this)) }
                }

                override fun onRunError(e: Exception?) {
                    trySend(Result.failure(e ?: Throwable("Cdc Error")))
                }
            }
            awaitClose {
                disconnect()
            }
        }
    }

    fun disconnect() {
        cdcTransfer?.stop(byteArrayOf(0xA5.toByte(), 0x04, 0x00, 0x04, 0x5A))
        cdcTransfer?.close()
        cdcTransfer = null
        eventFlow.update { State.Disconnected }
    }
}

class UsbConnection(
    override val name: String,
    override val realDevice: UsbDevice,
    eventFlow: MutableStateFlow<State>
) :
    Connection {
    private val channel1 = UsbCdcChannel("0", "channel1", realDevice, eventFlow)
    override fun channel1() = channel1

    override suspend fun connect(): Result<Unit> {
        return channel1.connect()
    }

    override fun disconnect() {
        channel1.disconnect()
    }
}