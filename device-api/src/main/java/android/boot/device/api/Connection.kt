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
    val channel1: Channel?
    val channel2: Channel?
        get() = null
    val channel3: Channel?
        get() = null
    val channel4: Channel?
        get() = null
    val channel5: Channel?
        get() = null
    val channel6: Channel?
        get() = null
    val channel7: Channel?
        get() = null
    val channel8: Channel?
        get() = null
    val channel9: Channel?
        get() = null

    fun config(configuration: T.() -> Unit)

    fun connect(connect: T.() -> Unit)

    fun disconnect(configuration: T.() -> Unit)
}
