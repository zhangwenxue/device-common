package android.boot.device.api

import kotlinx.coroutines.flow.Flow

interface DeviceDiscovery<T> {
    val deviceFlow: Flow<Result<List<Device<T>>>>
    fun discover(
        timeoutMills: Long = -1,
        vararg filters: String
    )

    fun cancel(): Result<Unit>
}