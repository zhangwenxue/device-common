package android.boot.device.ecg.nordicble

import android.Manifest
import android.annotation.SuppressLint
import android.boot.common.extensions.asString
import android.boot.common.provider.globalContext
import android.boot.device.api.Channel
import android.boot.device.api.ChannelNotFoundException
import android.boot.device.api.Connection
import android.boot.device.api.DeviceLog
import android.boot.device.ecg.util.ECG3GenParser
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import no.nordicsemi.android.kotlin.ble.core.ServerDevice
import no.nordicsemi.android.kotlin.ble.core.data.BleGattConnectOptions
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import java.util.UUID

val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

fun hasNoBluetoothConnectPermission() =
    blePermissions.all {
        ActivityCompat.checkSelfPermission(
            globalContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

fun unGrantedBluetoothPermissions() =
    blePermissions.filter {
        ActivityCompat.checkSelfPermission(
            globalContext,
            it
        ) != PackageManager.PERMISSION_GRANTED
    }


fun assertBluetoothConnectPermission() {
    if (hasNoBluetoothConnectPermission()) Log.e(
        "_BleConnection",
        "${unGrantedBluetoothPermissions().asString()} not granted!"
    )
}


class NordicCharacteristicChannel(
    override val id: String,
    override val name: String?,
    private val services: ClientBleGattServices,
    private val serviceUUid: String,
    private val characterUUid: String,
) : Channel {
    private var characteristic: ClientBleGattCharacteristic? = null

    @SuppressLint("MissingPermission")
    override suspend fun read(src: ByteArray, timeoutMillis: Int): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            runCatching {
                assertBluetoothConnectPermission()
                withTimeout(timeoutMillis.toLong()) {
                    getCharacteristic()?.read()?.value ?: throw ChannelNotFoundException
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    override suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                assertBluetoothConnectPermission()
                withTimeout(timeoutMillis.toLong()) {
                    getCharacteristic()?.write(DataByteArray(dest), BleWriteType.NO_RESPONSE)
                        ?: throw ChannelNotFoundException
                }
            }
        }
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        assertBluetoothConnectPermission()
        val writeRet = write(ECG3GenParser.packStartCollectCmd(), 500)
        if (writeRet.isFailure) {
            return flowOf(
                Result.failure(
                    writeRet.exceptionOrNull() ?: Throwable("Write error")
                )
            )
        }
        return getCharacteristic()?.getNotifications(bufferOverflow = BufferOverflow.SUSPEND)
            ?.filterIsInstance(DataByteArray::class)
            ?.filterNotNull()
            ?.map { Result.success(it.value) }
            ?: flowOf(Result.failure(ChannelNotFoundException))
    }


    override suspend fun stopListen(): Result<Unit> {
        assertBluetoothConnectPermission()
        return write(ECG3GenParser.packStopCollectCmd(), 500)
            .onSuccess {
                DeviceLog.log("<BLE> Stop listen success")
            }.onFailure {
                DeviceLog.log("<BLE> Stop listen failed", throwable = it)
            }

    }

    private fun getCharacteristic(): ClientBleGattCharacteristic? {
        return characteristic ?: services.findService(UUID.fromString(serviceUUid))
            ?.findCharacteristic(UUID.fromString(characterUUid))
            ?.apply { characteristic = this }
    }
}

abstract class NordicBleConnection(
    override val name: String,
    override val realDevice: ServerDevice,
) : Connection {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val mutex = Mutex()

    private var client: ClientBleGatt? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit> = mutex.withLock {
        if (client != null) {
            return Result.success(Unit)
        }
        return withContext(Dispatchers.IO) {
            DeviceLog.log("<BLE> Connecting...")
            runCatching {
                val client = ClientBleGatt.connect(
                    globalContext,
                    realDevice,
                    scope,
                    options = BleGattConnectOptions(autoConnect = true, closeOnDisconnect = true)
                )

                if (!client.isConnected) {
                    return@withContext Result.failure(RuntimeException("failed to connect to gatt!"))
                }
                this@NordicBleConnection.client = client
                val services = client.discoverServices()
                onConfigureChannel(client, services)
                return@withContext Result.success(Unit).also {
                    DeviceLog.log("<BLE> Connection established.")
                }
            }.onFailure {
                DeviceLog.log("Nordic BLE connect error", throwable = it)
                this@NordicBleConnection.client = null
            }
        }
    }

    open fun onConfigureChannel(client: ClientBleGatt, services: ClientBleGattServices) {}


    override suspend fun disconnect() {
        mutex.withLock {
            client?.close()
            client = null
        }
    }
}