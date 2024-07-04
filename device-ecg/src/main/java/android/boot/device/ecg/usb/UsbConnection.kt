package android.boot.device.ecg.usb

import android.boot.common.provider.globalContext
import android.boot.device.api.Channel
import android.boot.device.api.Connection
import android.boot.device.api.DeviceLog
import android.boot.device.api.ECGCommands
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UsbCdcChannel(
    override val id: String,
    override val name: String?,
    private val usbDevice: UsbDevice,
    private val ecgCommands: ECGCommands,
) : Channel {
    companion object {
        val UNCONNECTED_ERROR = Throwable("Usb connection unestablished!")
    }

    private val usbManager by lazy {
        globalContext.getSystemService(UsbManager::class.java)
    }
    private val driver by lazy {
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .firstOrNull { it.device == usbDevice }
    }

    private var cdcTransfer: UsbCdcTransfer? = null
    private val mutex = Mutex()

    suspend fun connect(): Result<Unit> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                if (cdcTransfer != null) {
                    return@withContext Result.success(Unit)
                }
                val connection = usbManager.openDevice(usbDevice)
                    ?: return@withContext Result.failure<Unit>(Throwable("Failed to create usb connection"))
                val port = driver?.ports?.firstOrNull()
                    ?: return@withContext Result.failure<Unit>(Throwable("Failed to find driver port"))
                cdcTransfer = UsbCdcTransfer(connection, port)
                val result = cdcTransfer?.ensureConnection()?.onFailure {
                    cdcTransfer = null
                } ?: Result.failure(Throwable("CdcTransfer not initialized"))
                result
            }
        }
    }

    override suspend fun read(src: ByteArray, timeoutMillis: Int) = runCatching {
        cdcTransfer?.read(src, timeoutMillis)?.getOrThrow() ?: throw UNCONNECTED_ERROR
    }

    override suspend fun write(dest: ByteArray, timeoutMillis: Int) = runCatching {
        cdcTransfer?.write(dest, timeoutMillis)?.getOrThrow() ?: throw UNCONNECTED_ERROR
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        val startRet = cdcTransfer
            ?.startEcgCollect()
            ?: Result.failure(UNCONNECTED_ERROR)

        if (startRet.isFailure) {
            return flowOf(Result.failure(UNCONNECTED_ERROR))
        }

        return callbackFlow {
            withContext(Dispatchers.IO) {
                while (cdcTransfer?.isCollecting() == true) {
                    cdcTransfer?.readFromBuffer()?.takeIf { it.isNotEmpty() }?.run {
                        trySend(Result.success(this))
                    }
                }
            }
            cdcTransfer?.runErrorAction = {
                trySend(Result.failure(it ?: Throwable("Usb CDC UnKnown Error")))
                close()
            }
            awaitClose {
                CoroutineScope(Dispatchers.IO).launch {
                    stopListen()
                    disconnect()
                }
            }
        }
    }

    override suspend fun stopListen(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            cdcTransfer?.stop(ecgCommands.stopCollect) ?: Result.success("No Need to stop listen")
                .also {
                    DeviceLog.log("_usb_transfer", "No need to stop listen")
                }
            Result.success(Unit)
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                cdcTransfer?.stop(ecgCommands.stopCollect)
                cdcTransfer?.close()
                cdcTransfer = null
            }
        }
    }
}

class UsbConnection(
    override val name: String,
    override val realDevice: UsbDevice,
    private val commands: ECGCommands
) :
    Connection {
    private val channel1 = UsbCdcChannel("0", "channel1", realDevice, commands)
    override fun channel1() = channel1

    override suspend fun connect(): Result<Unit> {
        return channel1.connect()
    }

    override suspend fun disconnect() {
        channel1.disconnect()
    }
}