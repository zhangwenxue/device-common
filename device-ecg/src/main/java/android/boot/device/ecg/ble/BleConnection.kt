package android.boot.device.ecg.ble

import android.annotation.SuppressLint
import android.boot.common.provider.globalContext
import android.boot.device.api.Channel
import android.boot.device.api.Connection
import android.content.pm.PackageManager
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.GattClientScope
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

fun hasNoBluetoothConnectPermission(): Boolean {
    return ActivityCompat.checkSelfPermission(
        globalContext,
        "android.permission.BLUETOOTH_CONNECT"
    ) != PackageManager.PERMISSION_GRANTED
}

fun assertBluetoothConnectPermission() {
    if (hasNoBluetoothConnectPermission()) throw RuntimeException("android.permission.BLUETOOTH_CONNECT permission not granted!")
}


class CharacteristicChannel(
    override val id: String,
    override val name: String?,
    private val gattScope: GattClientScope? = null,
    serviceUUid: String,
    characterUUid: String,
) :
    Channel {
    private val characteristic = gattScope
        ?.getService(UUID.fromString(serviceUUid))
        ?.getCharacteristic(UUID.fromString(characterUUid))

    override suspend fun read(src: ByteArray?, timeoutMillis: Int): Result<ByteArray> {
        return characteristic?.let { gattScope?.readCharacteristic(it) }
            ?: Result.failure(Throwable("Invalid characteristic"))
    }


    override suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit> {
        return characteristic?.let { gattScope?.writeCharacteristic(it, dest) } ?: Result.failure(
            Throwable("Invalid characteristic")
        )
    }

    override fun listen(): Flow<Result<ByteArray>> {
        return characteristic?.let {
            gattScope?.subscribeToCharacteristic(it)?.map { array -> Result.success(array) }
        } ?: flowOf(Result.failure(Throwable("Invalid characteristic")))
    }
}

abstract class BleConnection(
    override val name: String,
    override val realDevice: BluetoothDevice,
) : Connection<BluetoothDevice> {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val bluetoothLe by lazy {
        BluetoothLe(globalContext)
    }
    private var gattScope: GattClientScope? = null

    override fun config(configuration: BluetoothDevice.() -> Unit) {
        configuration(realDevice)
    }

    @SuppressLint("MissingPermission")
    override fun connect(connect: BluetoothDevice.() -> Unit) {
        assertBluetoothConnectPermission()
        connect(realDevice)
        scope.launch {
            bluetoothLe.connectGatt(realDevice) {
                gattScope = this
            }
        }
    }


    override fun disconnect(configuration: BluetoothDevice.() -> Unit) {

    }


    internal fun createCharacteristicChannel(
        serviceUUid: String,
        characterUUid: String,
        order: Int = 0
    ): Channel {
        return CharacteristicChannel(
            id = "$order",
            name = "Channel-$order",
            gattScope,
            serviceUUid,
            characterUUid
        )
    }
}