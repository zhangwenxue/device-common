package android.boot.device.ecg.usb

import android.boot.device.api.ChannelNotFoundException
import android.boot.device.api.Connection
import android.boot.device.api.ECGCommands
import android.boot.device.api.ECGDevice
import android.boot.device.api.Gen
import android.boot.device.api.Transmission
import android.boot.device.ecg.util.ECG3GenParser
import android.hardware.usb.UsbDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class UsbEcg(
    override val name: String = "Usb Ecg 3Gen",
    override val mac: String = "",
    override val realDevice: UsbDevice,
    override val transmission: Transmission = Transmission.Usb,
    override val gen: Gen = Gen.Gen3,
    override val ecgCommands: ECGCommands = Usb3GenCommands()
) : ECGDevice {

    override val connection: Connection = UsbConnection(name, realDevice, ecgCommands)

    override suspend fun read(
        dest: ByteArray,
        timeoutMillis: Int,
        autoClose: Boolean
    ) = runCatching {
        assertConnection()
        (connection.channel1()?.read(dest, timeoutMillis)
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
        (connection.channel1()?.write(dest, timeoutMillis)
            ?.getOrThrow()
            ?: throw ChannelNotFoundException).also {
            if (autoClose) connection.disconnect()
        }
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        val writeRet = write(ecgCommands.startCollect, 100, false)
        if (writeRet.isFailure) {
            val error =
                writeRet.exceptionOrNull() ?: Throwable("Listen failure,write start cmd failure")
            return flowOf(Result.failure(error))
        }
        return connection.channel1()?.listen() ?: flowOf()
    }

    override suspend fun stopListen() = runCatching {
        connection.channel1()?.stopListen()?.getOrThrow() ?: throw ChannelNotFoundException
    }

    override suspend fun readSN(autoClose: Boolean) = runCatching {
        val readRet = read(ecgCommands.readSN, 2000, autoClose)
        ecgCommands.snParser(readRet.getOrThrow()).getOrThrow()
    }

    override suspend fun writeSN(sn: String, autoClose: Boolean) = runCatching {
        write(ecgCommands.snPackager(sn).getOrThrow(), 2000, autoClose).getOrThrow()
    }


    override suspend fun readVersion(autoClose: Boolean) = runCatching {
        val result = read(ecgCommands.readVersion, 2000, autoClose)
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