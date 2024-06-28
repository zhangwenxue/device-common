package android.boot.device.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface DeviceDiscovery {
    fun discover(
        scope: CoroutineScope? = null,
        timeoutMills: Long = -1,
        vararg deviceFilters: DeviceFilter
    ): Flow<Result<List<ECGDevice>>>

    fun stop(): Result<Unit>
}