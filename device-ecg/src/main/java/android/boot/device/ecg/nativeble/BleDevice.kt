package android.boot.device.ecg.nativeble

import android.boot.device.api.Channel
import android.boot.device.api.ECGDevice
import android.boot.device.api.Gen
import android.boot.device.api.State
import android.boot.device.api.Transmission
import androidx.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf


class Ble3GConnection(
    name: String,
    realDevice: BluetoothDevice,
    eventFlow: MutableStateFlow<State>,
) : BleConnection(name, realDevice, eventFlow) {
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
    override val transmission: Transmission = Transmission.Ble,
    override val gen: Gen = Gen.Gen3,
) : ECGDevice {
    private val _eventFlow: MutableStateFlow<State> = MutableStateFlow(State.Idle)
    private val realConnection = Ble3GConnection("Ble3GConnection", realDevice, _eventFlow)
    override val connection = realConnection
    override val eventFlow = _eventFlow

    override suspend fun read(
        dest: ByteArray?,
        timeoutMillis: Int,
        autoClose: Boolean
    ): Result<ByteArray> {
        val connected = connection.connect()
        if (connected.isFailure) return Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}"))
        return connection.channel1().read(dest, timeoutMillis).also {
            if (autoClose) close()
        }
    }

    override suspend fun write(
        dest: ByteArray,
        timeoutMillis: Int,
        autoClose: Boolean
    ): Result<Unit> {
        val connected = connection.connect()
        if (connected.isFailure) return Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}"))
        return connection.channel1().write(dest, timeoutMillis).also {
            if (autoClose) close()
        }
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