package android.boot.device.api

import kotlinx.coroutines.flow.Flow

interface Device<T> {
    val name: String
    val mac: String
    val realDevice: T
    val connection: Connection<T>
    suspend fun read(dest: ByteArray? = null, timeoutMillis: Int): Result<ByteArray>
    suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit>
    fun listen(): Flow<Result<ByteArray>>
}
