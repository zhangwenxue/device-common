package android.boot.device.ecg.nordicble

import android.boot.device.api.Channel
import android.boot.device.api.ChannelNotFoundException
import android.boot.device.api.DeviceLog
import android.boot.device.api.ECGDevice
import android.boot.device.api.Gen
import android.boot.device.api.State
import android.boot.device.api.Transmission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import no.nordicsemi.android.kotlin.ble.core.ServerDevice


class NordicBle3GConnection(
    name: String,
    realDevice: ServerDevice,
    eventFlow: MutableStateFlow<State>,
) : NordicBleConnection(name, realDevice, eventFlow) {
    private var channel1: Channel? = null
    private var channel2: Channel? = null

    override fun onConfigureChannel(client: ClientBleGatt, services: ClientBleGattServices) {
        super.onConfigureChannel(client, services)
        channel1 = NordicCharacteristicChannel(
            "0", "channel1",
            services,
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe41-8e22-4541-9d4c-21edae82ed19",
        )

        channel2 = NordicCharacteristicChannel(
            "1", "channel2",
            services,
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe42-8e22-4541-9d4c-21edae82ed19",
        )
    }

    override fun channel1() = channel1

    override fun channel2() = channel2
}

class NordicBleEcg3G(
    override val realDevice: ServerDevice,
    override val mac: String,
    override val name: String = "三代机Ble",
    override val transmission: Transmission = Transmission.Ble,
    override val gen: Gen = Gen.Gen3,
) : ECGDevice {
    private val _eventFlow: MutableStateFlow<State> = MutableStateFlow(State.Idle)
    private val realConnection = NordicBle3GConnection("Ble3GConnection", realDevice, _eventFlow)
    override val connection = realConnection
    override val eventFlow = _eventFlow

    override suspend fun read(
        dest: ByteArray?,
        timeoutMillis: Int,
        autoClose: Boolean
    ): Result<ByteArray> {
        val connected = connection.connect()
        if (connected.isFailure) return Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}"))
        return (connection.channel1()?.read(dest, timeoutMillis) ?: Result.failure(
            ChannelNotFoundException
        )).also {
            if (autoClose) connection.disconnect()
        }
    }

    override suspend fun write(
        dest: ByteArray,
        timeoutMillis: Int,
        autoClose: Boolean
    ): Result<Unit> {
        val connected = connection.connect()
        if (connected.isFailure) return Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}"))
        return (connection.channel1()?.write(dest, timeoutMillis) ?: Result.failure(
            ChannelNotFoundException
        )).also {
            if (autoClose) connection.disconnect()
        }
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        DeviceLog.log("<BLE> prepare to listen,checking connection...")
        val connected = connection.connect()
        if (connected.isFailure) return flowOf<Result<ByteArray>>(Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}"))).also {
            DeviceLog.log("<BLE> Failed to establish ble connection:${connected.exceptionOrNull()?.message}")
        }
        DeviceLog.log("<BLE> Write collect cmd bytes")
        write(byteArrayOf(0xA5.toByte(), 0x09, 0x00, 0x09, 0x5A), 100, false)
        DeviceLog.log("<BLE> Starting listening")
        return connection.channel2()?.listen() ?: flowOf()
    }

    override suspend fun stopListen(): Result<Unit> {
        DeviceLog.log("<BLE> Stopping listening")
        return write(byteArrayOf(0xA5.toByte(), 0x04, 0x00, 0x04, 0x5A), 100, false).onSuccess {
            DeviceLog.log("<BLE> Stop listen success")
        }.onFailure {
            DeviceLog.log("<BLE> Stop listen failed", throwable = it)
        }
    }

    override fun close() {
        connection.disconnect()
    }

}