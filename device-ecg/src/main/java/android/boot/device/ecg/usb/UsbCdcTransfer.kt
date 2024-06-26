package android.boot.device.ecg.usb

import android.hardware.usb.UsbDeviceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialPort.PARITY_NONE
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.hoho.android.usbserial.util.SerialInputOutputManager.Listener
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

    var listener: Listener? = null

    constructor(connection: UsbDeviceConnection, port: UsbSerialPort) : this(
        UsbConfig(connection, port)
    )

    private val usbIoManager by lazy {
        SerialInputOutputManager(config.port, this)
    }

    @Volatile
    private var isCollecting = false

    private val readWriteBuffer = ReadWriteBuffer(CACHE_BUFFER_SIZE)

    private val handler by lazy {
        HandlerThread("cdc_worker").let {
            it.start()
            Handler(it.looper)
        }
    }

    private var stopCmd: ByteArray? = null


    fun openDevice(): Result<Unit> {
        return runCatching {
            Log.i(TAG, "open usb device")
            config.run {
                if (port.isOpen) return@runCatching
                port.open(connection)
                port.setParameters(baudRate, dataBits, stopBits, parity)
                port.dtr = true
            }
        }
    }

    fun startEcgCollect(command: ByteArray) {
        Log.i(TAG, "start ecg collect")
        if (usbIoManager.state != SerialInputOutputManager.State.STOPPED) return
        config.run {
            usbIoManager.readBufferSize = USB_TRANSFER_BUFFER_SIZE
            Log.i(TAG, "read buffer size:${usbIoManager.readBufferSize}")
            usbIoManager.start()
            usbIoManager.readBufferSize
            port.write(command, WRITE_WAIT_MILLIS.toInt())
            isCollecting = true
            ReadThread().start()
        }
    }

    fun stop(command: ByteArray) {
        if (usbIoManager.state == SerialInputOutputManager.State.STOPPED) return
        Log.i(TAG, "stop usb port")
        stopCmd = command
        isCollecting = false
        if (config.port.isOpen) runCatching {
            config.port.write(
                command,
                READ_WAIT_MILLIS.toInt()
            )
            usbIoManager.stop()
        }
    }

    fun close() {
        isCollecting = false
        Log.i(TAG, "close usb port")
        if (config.port.isOpen)
            config.port.close()
        runCatching { handler.looper.quit() }
    }

    //    var no = -1
    override fun onNewData(data: ByteArray?) {
        data?.run {
            readWriteBuffer.write(this)
        }
    }

    override fun onRunError(e: Exception?) {
        listener?.onRunError(e)
        Log.e(TAG, "onRunError", e)
        stopCmd?.let { stop(it) }
        close()
    }

    inner class ReadThread : Thread() {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isCollecting) {
                val ret = readWriteBuffer.read()
                runCatching {
                    handler.post {
                        if (ret.isNotEmpty()) {
                            listener?.onNewData(ret)
                        }
                    }
                }
            }
        }
    }
}