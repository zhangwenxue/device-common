package android.boot.device.ecg.ble

import android.boot.device.api.Channel
import android.boot.device.api.ChannelNotFoundException
import android.boot.device.api.Connection
import android.boot.device.api.Device
import androidx.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf


class DeviceConnection(
    name: String,
    realDevice: BluetoothDevice,
) : BleConnection(name, realDevice) {
    // Write channel
    override val channel1: Channel
        get() = createCharacteristicChannel(
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe41-8e22-4541-9d4c-21edae82ed19"
        )

    // Notify channel
    override val channel2: Channel
        get() = createCharacteristicChannel(
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe42-8e22-4541-9d4c-21edae82ed19"
        )
}

class BleEcg3G(
    override val realDevice: BluetoothDevice,
    override val mac: String,
    override val name: String = "三代机Ble",
) : Device<BluetoothDevice> {
    override val connection: Connection<BluetoothDevice>
        get() = DeviceConnection("Ble2GConnection", realDevice)

    override suspend fun read(dest: ByteArray?, timeoutMillis: Int): Result<ByteArray> {
        return connection.channel1?.read(dest, timeoutMillis) ?: Result.failure(
            ChannelNotFoundException
        )
    }

    override suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit> {
        return connection.channel1?.write(dest, timeoutMillis) ?: Result.failure(
            ChannelNotFoundException
        )
    }

    override fun listen(): Flow<Result<ByteArray>> {
        return connection.channel2?.listen() ?: flowOf(Result.failure(ChannelNotFoundException))
    }

}