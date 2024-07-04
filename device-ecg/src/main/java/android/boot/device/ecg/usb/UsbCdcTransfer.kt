package android.boot.device.ecg.usb

import android.boot.common.extensions.asHexString
import android.boot.common.extensions.i
import android.boot.device.api.DeviceLog
import android.hardware.usb.UsbDeviceConnection
import android.os.SystemClock
import android.util.Log
import com.hoho.android.usbserial.driver.CommonUsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialPort.PARITY_NONE
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.hoho.android.usbserial.util.SerialInputOutputManager.Listener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantReadWriteLock

data class UsbConfig(
    val connection: UsbDeviceConnection,
    val port: UsbSerialPort,
    val baudRate: Int = 921600,
    val dataBits: Int = 8,
    val stopBits: Int = UsbSerialPort.STOPBITS_1,
    val parity: Int = PARITY_NONE
)

class ReadWriteBuffer(private val bufferSize: Int) {
    private val buffer = ByteArray(bufferSize)
    private var position = 0
    private val lock = ReentrantReadWriteLock()

    fun write(data: ByteArray) {
        lock.writeLock().lock()
        try {
            if (position + data.size > bufferSize) {
                Log.e("Buffer", "Buffer overflow")
                return
            }
            System.arraycopy(data, 0, buffer, position, data.size)
            position += data.size
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun read(): ByteArray {
        lock.readLock().lock()
        return try {
            val data = buffer.copyOfRange(0, position)
            clearBuffer()
            data
        } finally {
            lock.readLock().unlock()
        }
    }

    private fun clearBuffer() {
        position = 0
        buffer.fill(0)
    }
}

class UsbCdcTransfer(private val config: UsbConfig) : Listener {
    companion object {
        private const val READ_WAIT_MILLIS = 0L
        private const val WRITE_WAIT_MILLIS = 0L
        private const val PACK_SIZE = 31
        private const val USB_TRANSFER_BUFFER_SIZE = 31 * 200
        private const val TAG = "_usb_transfer"
        private const val CACHE_BUFFER_SIZE = 31 * 1000
    }

    var runErrorAction: ((Throwable?) -> Unit)? = null

    private val writeLock = Mutex()

    @Volatile
    private var isCollecting = false

    constructor(connection: UsbDeviceConnection, port: UsbSerialPort) : this(
        UsbConfig(connection, port)
    )

    init {
        SerialInputOutputManager.DEBUG = true
        CommonUsbSerialPort.DEBUG = true
    }

    private val usbIoManager by lazy {
        SerialInputOutputManager(config.port, this)
    }

    private val readWriteBuffer = ReadWriteBuffer(CACHE_BUFFER_SIZE)


    fun startEcgCollect(): Result<Unit> {
        return runCatching {
            Log.i(TAG, "${this@UsbCdcTransfer}-start ecg collect")
            if (isCollecting()) return@runCatching
            usbIoManager.start()
            isCollecting = true
        }
    }

    suspend fun stop(command: ByteArray) {
        if (notCollecting()) {
            return
        }
        isCollecting = false
        write(command, 2000)
        usbIoManager.stop()
    }


    suspend fun write(command: ByteArray, timeoutMills: Int): Result<Unit> {
        if (isCollecting()) return Result.failure(Throwable("Usb write not support while collecting data"))
        return runCatching {
            withContext(Dispatchers.IO) {
                writeLock.withLock {
                    config.port.write(command, timeoutMills)
                }
            }
        }
    }

    suspend fun read(command: ByteArray, timeoutMills: Int): Result<ByteArray> {
        if (command.isEmpty()) return Result.failure(Throwable("Invalid read params"))
        if (isCollecting()) return Result.failure(Throwable("Usb read not support while collecting data"))
        return runCatching {
            withContext(Dispatchers.IO) {
                writeLock.withLock {
                    DeviceLog.log("Reading CMD ${command.asHexString()}")
                    config.port.write(command, timeoutMills)
                    var finished = false
                    val start = SystemClock.elapsedRealtime()
                    var response: ByteArray? = null
                    while (!finished && SystemClock.elapsedRealtime() - start < 2000) {
                        delay(10)
                        val buffer = ByteArray(64)
                        val len = config.port.read(buffer, 500)
                        DeviceLog.log("Reading $len ${buffer.asHexString()}")
                        if (len > 2 && buffer[0] == command[0] && buffer[1] == command[1]) {
                            response = ByteArray(len)
                            System.arraycopy(buffer, 0, response, 0, len)
                            finished = true
                        }
                    }
                    response ?: throw TimeoutException("Read timeout")
                }
            }
        }
    }

    fun close() {
        Log.i(TAG, "${this@UsbCdcTransfer}-close usb port")
        runCatching {
            config.port.close()
            usbIoManager.stop()
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        DeviceLog.i("Usb-Collecting", data.asHexString())
        readWriteBuffer.write(data)
//
//        if (writeInfo.filter?.let { it(data) } == true) {
//            "$writeInfo wait hit :${data.asHexString()}".toast()
//            writeInfo = writeInfo.copy(response = data)
//            writeInfo.latch?.countDown()
//        } else {
//            "$writeInfo wait missed:${data.asHexString()}".toast()
//        }
    }

    override fun onRunError(e: Exception?) {
        runErrorAction?.invoke(e)
        Log.e(TAG, "${this@UsbCdcTransfer}-onRunError", e)
    }

    fun isCollecting(): Boolean {
        return isCollecting
    }

    private fun notCollecting(): Boolean {
        return isCollecting.not()
    }

    suspend fun ensureConnection() = withContext(Dispatchers.IO) {
        if (config.port.isOpen && usbIoManager.state == SerialInputOutputManager.State.RUNNING) {
            Result.success(Unit)
        } else {
            Log.i(TAG, "${this@UsbCdcTransfer}-open usb device")
            kotlin.runCatching {
                config.run {
                    if (!port.isOpen) {
                        port.open(connection)
                    }
                    port.setParameters(baudRate, dataBits, stopBits, parity)
                    port.dtr = true
                    usbIoManager.readBufferSize = USB_TRANSFER_BUFFER_SIZE
                }
            }
        }
    }

    fun readFromBuffer(): ByteArray {
        return readWriteBuffer.read()
    }
}