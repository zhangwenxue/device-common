package android.boot.device.api

import kotlinx.coroutines.flow.Flow

enum class Transmission {
    Ble, Bt, Usb
}

enum class Gen {
    Gen2, Gen3
}

sealed class State {
    data object Idle : State()
    data object Connecting : State()
    data object Connected : State()
    data object Disconnected : State()
}


interface ECGDevice {
    val name: String
    val mac: String
    val realDevice: Any
    val connection: Connection
    val transmission: Transmission
    val gen: Gen
    val eventFlow: Flow<State>
    suspend fun read(
        dest: ByteArray,
        timeoutMillis: Int,
        autoClose: Boolean
    ): Result<ByteArray>

    suspend fun write(dest: ByteArray, timeoutMillis: Int, autoClose: Boolean): Result<Unit>
    suspend fun listen(): Flow<Result<ByteArray>>
    suspend fun stopListen(): Result<Unit>
    suspend fun readSN(autoClose: Boolean): Result<String>
    suspend fun writeSN(sn:String,autoClose: Boolean): Result<Unit>
    suspend fun readVersion(autoClose: Boolean): Result<String>
    fun close()
}
