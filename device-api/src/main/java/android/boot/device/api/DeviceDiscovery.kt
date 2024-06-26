package android.boot.device.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface DeviceDiscovery {
    val deviceFlow: Flow<Result<List<ECGDevice>>>
    fun discover(
        scope: CoroutineScope? = null,
        timeoutMills: Long = -1,
        vararg deviceFilters: DeviceFilter
    )

    fun stop(): Result<Unit>
}