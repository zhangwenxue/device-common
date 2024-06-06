package android.boot.device.api

import kotlinx.coroutines.flow.Flow

data object ChannelNotFoundException : Throwable("Channel not found!")
data object ConnectionNotFoundException : Throwable("Connection not found!")

interface Channel {
    val id: String
    val name: String?

    suspend fun read(src: ByteArray? = null, timeoutMillis: Int): Result<ByteArray>
    suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit>
    fun listen(): Flow<Result<ByteArray>>
}

interface Connection<T> {
    val name: String
    val realDevice: T
    fun channel1(): Channel?
    fun channel2(): Channel? = null
    fun channel3(): Channel? = null
    fun channel4(): Channel? = null
    fun channel5(): Channel? = null
    fun channel6(): Channel? = null
    fun channel7(): Channel? = null
    fun channel8(): Channel? = null
    fun channel9(): Channel? = null

    fun config(configuration: T.() -> Unit)

    suspend fun connect(): Result<Unit>

    fun disconnect()
}
