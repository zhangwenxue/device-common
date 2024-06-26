package android.boot.device.ecg.usb

import android.boot.device.api.ChannelNotFoundException
import android.boot.device.api.Connection
import android.boot.device.api.ECGDevice
import android.boot.device.api.Gen
import android.boot.device.api.State
import android.boot.device.api.Transmission
import android.hardware.usb.UsbDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class UsbEcg(
    override val name: String = "Usb Ecg 3Gen",
    override val mac: String = "",
    override val realDevice: UsbDevice,
    override val transmission: Transmission = Transmission.Usb,
    override val gen: Gen = Gen.Gen3
) : ECGDevice {
    private val _eventFlow: MutableStateFlow<State> = MutableStateFlow(State.Idle)
    override val eventFlow: Flow<State> = _eventFlow
    override val connection: Connection = UsbConnection(name, realDevice,_eventFlow)

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
        val connected = connection.connect()
        if (connected.isFailure) return flowOf(Result.failure(Throwable("Device connection failure:${connected.exceptionOrNull()?.message}")))
        return connection.channel1()?.listen() ?: flowOf()

    }

    override fun close() {
        connection.disconnect()
    }

}