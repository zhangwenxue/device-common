package android.boot.device.ecg.nordicble

import android.annotation.SuppressLint
import android.boot.common.provider.globalContext
import android.boot.device.api.DeviceDiscovery
import android.boot.device.api.DeviceFilter
import android.boot.device.api.ECGDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanFilter
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class NordicBleDiscovery : DeviceDiscovery {

    private val innerScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var scanJobs: Job? = null

    private val _scanResultFlow = MutableStateFlow<Result<List<ECGDevice>>>(
        Result.success(
            emptyList()
        )
    )

    @OptIn(FlowPreview::class)
    @SuppressLint("MissingPermission")
    override fun discover(
        scope: CoroutineScope?,
        timeoutMills: Long,
        vararg deviceFilters: DeviceFilter
    ) {
        val workScope = scope ?: innerScope

        scanJobs?.cancel()

        val aggregator = BleScanResultAggregator()

        val scanFilters = deviceFilters.map { BleScanFilter(deviceName = it.name) }

        if (scanFilters.isEmpty()) {
            workScope.launch {
                _scanResultFlow.emit(
                    Result.failure(Throwable("请指定设备名称进行扫描"))
                )
            }
            return
        }

        scanJobs = BleScanner(globalContext)
            .scan(scanFilters)
            .timeout(if (timeoutMills > 0) timeoutMills.milliseconds else Duration.INFINITE)
            .filter {
                scanFilters.mapNotNull { filter -> filter.deviceName }.contains(it.device.name)
            }
            .catch {
                if (it is TimeoutCancellationException) {
                    if (_scanResultFlow.value.getOrNull().isNullOrEmpty()) {
                        _scanResultFlow.emit(
                            Result.failure(Throwable("No device found in $timeoutMills ms"))
                        )
                    }
                } else {
                    throw it
                }
            }
            .map { aggregator.aggregateDevices(it) }
            .onEach {
                _scanResultFlow.emit(
                    Result.success(it.map { device ->
                        NordicBleEcg3G(
                            device,
                            device.address,
                            device.name ?: ""
                        )
                    })
                )
            }
            .stateIn(
                scope = workScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = null
            ).launchIn(workScope)
    }

    override val deviceFlow: Flow<Result<List<ECGDevice>>>
        get() = _scanResultFlow


    override fun stop(): Result<Unit> {
        scanJobs?.cancel()
        return Result.success(Unit)
    }

}