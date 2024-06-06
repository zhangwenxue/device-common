package android.boot.device.ecg.ble

import android.boot.device.api.Channel
import android.boot.device.api.Device
import androidx.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf


class Ble3GConnection(
    name: String,
    realDevice: BluetoothDevice,
) : BleConnection(name, realDevice) {
    override fun channel1(): Channel {
        return createCharacteristicChannel(
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe41-8e22-4541-9d4c-21edae82ed19"
        )
    }

    override fun channel2(): Channel {
        return createCharacteristicChannel(
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe42-8e22-4541-9d4c-21edae82ed19"
        )
    }
}

class BleEcg3G(
    override val realDevice: BluetoothDevice,
    override val mac: String,
    override val name: String = "三代机Ble",
) : Device<BluetoothDevice> {
    private val realConnection = Ble3GConnection("Ble3GConnection", realDevice)
    override val connection = realConnection

    override suspend fun read(dest: ByteArray?, timeoutMillis: Int): Result<ByteArray> {
        val connected = connection.connect()
        if (connected.isFailure) return Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}"))
        return connection.channel1().read(dest, timeoutMillis)
    }

    override suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit> {
        val connected = connection.connect()
        if (connected.isFailure) return Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}"))
        return connection.channel1().write(dest, timeoutMillis)
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        val connected = connection.connect()
        if (connected.isFailure) return flowOf(Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}")))
        return connection.channel2().listen()
    }

    override fun close() {
        connection.disconnect()
    }

}