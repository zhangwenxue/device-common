package android.boot.device.ecg.nordicble

import android.annotation.SuppressLint
import android.boot.common.provider.globalContext
import android.boot.device.api.Channel
import android.boot.device.api.ChannelNotFoundException
import android.boot.device.api.Connection
import android.boot.device.api.DeviceLog
import android.boot.device.api.State
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import no.nordicsemi.android.kotlin.ble.core.ServerDevice
import no.nordicsemi.android.kotlin.ble.core.data.BleGattConnectionStatus
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.data.GattConnectionState
import java.util.UUID

fun hasNoBluetoothConnectPermission(): Boolean {
    return ActivityCompat.checkSelfPermission(
        globalContext,
        "android.permission.BLUETOOTH_CONNECT"
    ) != PackageManager.PERMISSION_GRANTED
}

fun assertBluetoothConnectPermission() {
    if (hasNoBluetoothConnectPermission()) Log.e(
        "_BleConnection",
        "android.permission.BLUETOOTH_CONNECT permission not granted!"
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
    override suspend fun read(src: ByteArray?, timeoutMillis: Int): Result<ByteArray> {
        return runCatching {
            withTimeout(timeoutMillis.toLong()) {
                getCharacteristic()?.read()?.value ?: throw throw ChannelNotFoundException
            }
        }
    }


    @SuppressLint("MissingPermission")
    override suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit> {
        return runCatching {
            withTimeout(timeoutMillis.toLong()) {
                getCharacteristic()?.apply {
                    Log.i("_Properties", this.properties.joinToString())
                }?.write(DataByteArray(dest), BleWriteType.NO_RESPONSE)
                    ?: throw ChannelNotFoundException
            }
        }
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        return getCharacteristic()?.getNotifications(bufferOverflow = BufferOverflow.SUSPEND)
            ?.map {
                Result.success(it.value)
            } ?: flowOf(Result.failure(ChannelNotFoundException))
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
    private val eventFlow: MutableStateFlow<State>,
) : Connection {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val mutex = Mutex()

    private var client: ClientBleGatt? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit> = mutex.withLock {
        if (this.client != null) {
            eventFlow.update { State.Connected }
            return Result.success(Unit)
        }

        eventFlow.update { State.Connecting }
        runCatching {
            val client = ClientBleGatt.connect(globalContext, realDevice, scope)
            client.connectionStateWithStatus.collectLatest {
                it?.run {
                    DeviceLog.log("NordicS&S", "state:$state,status:$status")
                    when {
                        state == GattConnectionState.STATE_DISCONNECTED -> eventFlow.update { State.Disconnected }
                        status == BleGattConnectionStatus.UNKNOWN -> eventFlow.update { State.Disconnected }
                        status == BleGattConnectionStatus.SUCCESS -> eventFlow.update { State.Connected }
                        status == BleGattConnectionStatus.TERMINATE_LOCAL_HOST -> eventFlow.update { State.Disconnected }
                        status == BleGattConnectionStatus.TERMINATE_PEER_USER -> eventFlow.update { State.Disconnected }
                        status == BleGattConnectionStatus.LINK_LOSS -> eventFlow.update { State.Disconnected }
                        status == BleGattConnectionStatus.CANCELLED -> eventFlow.update { State.Disconnected }
                        status == BleGattConnectionStatus.TIMEOUT -> eventFlow.update { State.Disconnected }
                    }

                }
            }
            if (!client.isConnected) {
                eventFlow.update { State.Disconnected }
                return Result.failure(RuntimeException("failed to connect to gatt!"))
            }
            this.client = client
            val services = client.discoverServices()
            onConfigureChannel(client, services)
            eventFlow.update { State.Connected }
            return Result.success(Unit)
        }.onFailure { eventFlow.update { State.Disconnected } }
    }

    open fun onConfigureChannel(client: ClientBleGatt, services: ClientBleGattServices) {}


    override fun disconnect() {
        client?.close()
        client = null
        eventFlow.update { State.Disconnected }
    }
}