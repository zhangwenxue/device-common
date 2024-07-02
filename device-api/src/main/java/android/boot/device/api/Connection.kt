package android.boot.device.api

import kotlinx.coroutines.flow.Flow

data object ChannelNotFoundException : Throwable("Channel not found!") {
    private fun readResolve(): Any = ChannelNotFoundException
}

data object ConnectionNotFoundException : Throwable("Connection not found!") {
    private fun readResolve(): Any = ConnectionNotFoundException
}

interface Channel {
    val id: String
    val name: String?

    suspend fun read(src: ByteArray, timeoutMillis: Int): Result<ByteArray>
    suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit>
    suspend fun listen(): Flow<Result<ByteArray>>
    suspend fun stopListen(): Result<Unit>
}

interface Connection {
    val name: String
    val realDevice: Any
    fun channel1(): Channel?
    fun channel2(): Channel? = null
    fun channel3(): Channel? = null
    fun channel4(): Channel? = null
    fun channel5(): Channel? = null
    fun channel6(): Channel? = null
    fun channel7(): Channel? = null
    fun channel8(): Channel? = null
    fun channel9(): Channel? = null

    suspend fun connect(): Result<Unit>

    suspend fun disconnect()
}
