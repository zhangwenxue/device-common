package android.boot.device.ecg.nordicble

import android.boot.common.extensions.asHexString
import android.boot.device.api.Channel
import android.boot.device.api.ChannelNotFoundException
import android.boot.device.api.DeviceLog
import android.boot.device.api.ECGCommands
import android.boot.device.api.ECGDevice
import android.boot.device.api.Gen
import android.boot.device.api.Transmission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import no.nordicsemi.android.kotlin.ble.core.ServerDevice


class NordicBle3GConnection(
    name: String,
    realDevice: ServerDevice,
    private val ecgCommands: ECGCommands,
) : NordicBleConnection(name, realDevice) {
    private var channel1: Channel? = null
    private var channel2: Channel? = null

    override fun onConfigureChannel(client: ClientBleGatt, services: ClientBleGattServices) {
        super.onConfigureChannel(client, services)
        channel1 = NordicCharacteristicChannel(
            "0", "channel1",
            services,
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe41-8e22-4541-9d4c-21edae82ed19",
            ecgCommands
        )

        channel2 = NordicCharacteristicChannel(
            "1", "channel2",
            services,
            "0000fe40-cc7a-482a-984a-7f2ed5b3e58f",
            "0000fe42-8e22-4541-9d4c-21edae82ed19",
            ecgCommands
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
    override val ecgCommands: ECGCommands = BLE3GenCommands()
) : ECGDevice {
    private val realConnection = NordicBle3GConnection("Ble3GConnection", realDevice, ecgCommands)
    override val connection = realConnection

    override suspend fun read(
        dest: ByteArray,
        timeoutMillis: Int,
        autoClose: Boolean
    ) = runCatching {
        assertConnection()
        (connection.channel1()
            ?.read(dest, timeoutMillis)
            ?.getOrThrow()
            ?: throw ChannelNotFoundException).also {
            if (autoClose) connection.disconnect()
        }
    }

    override suspend fun write(
        dest: ByteArray,
        timeoutMillis: Int,
        autoClose: Boolean
    ) = runCatching {
        assertConnection()
        (connection.channel1()
            ?.write(dest, timeoutMillis)
            ?.getOrThrow()
            ?: throw ChannelNotFoundException).also {
            if (autoClose) connection.disconnect()
        }
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        val ret = write(ecgCommands.startCollect, 500, false)
        if (ret.isFailure) {
            val error = ret.exceptionOrNull() ?: Throwable("Write start collect cmd error")
            return flowOf(Result.failure(error))
        }
        return connection.channel2()?.listen()
            ?.also { DeviceLog.log("<BLE> connection.channel2() is null!") } ?: flowOf()
    }

    override suspend fun stopListen() = runCatching {
        connection.channel1()?.stopListen()
        Unit
    }

    override suspend fun readSN(autoClose: Boolean) = runCatching {
        val ret = read(ecgCommands.readSN, 500, false).getOrThrow()
        ecgCommands.snParser(ret).getOrThrow()
    }

    override suspend fun writeSN(sn: String, autoClose: Boolean) =
        runCatching {
            val writeSNCmd = ecgCommands.snPackager(sn).getOrThrow()
            DeviceLog.log("WriteSN:$sn\n${writeSNCmd.asHexString()}")
            write(writeSNCmd, 200, false).getOrThrow()
        }


    override suspend fun readVersion(autoClose: Boolean) = runCatching {
        val result = read(ecgCommands.readVersion, 200, autoClose)
        ecgCommands.versionParser(result.getOrThrow()).getOrThrow()
    }

    override suspend fun close() {
        connection.disconnect()
    }

    @Throws(Throwable::class)
    private suspend fun assertConnection() {
        connection.connect().getOrThrow()
    }

}