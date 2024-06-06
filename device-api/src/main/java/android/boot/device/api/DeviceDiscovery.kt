package android.boot.device.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface DeviceDiscovery<T> {
    val deviceFlow: Flow<Result<List<Device<T>>>>
    fun discover(
        scope: CoroutineScope? = null,
        timeoutMills: Long = -1,
        vararg deviceFilters: DeviceFilter
    )

    fun cancel(): Result<Unit>
}