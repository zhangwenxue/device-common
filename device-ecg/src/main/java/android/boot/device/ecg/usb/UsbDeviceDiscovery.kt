package android.boot.device.ecg.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.boot.common.provider.globalContext
import android.boot.device.api.DeviceDiscovery
import android.boot.device.api.DeviceFilter
import android.boot.device.api.DeviceLog
import android.boot.device.api.ECGDevice
import android.boot.device.api.Gen
import android.boot.device.api.Transmission
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

sealed class UsbId(val usbFilter: (Int, Int) -> Boolean) {
    data object Usb2G :
        UsbId(usbFilter = { vendorId, productId -> vendorId == 4292 || productId == 60000 || productId == 60001 })

    data object Usb3G :
        UsbId(usbFilter = { vendorId, productId -> vendorId == 1155 && productId == 22336 })
}

val UsbDevice.asEcgDevice: ECGDevice?
    get() {
        return when {
            UsbId.Usb2G.usbFilter(vendorId, productId) -> UsbEcg(realDevice = this, gen = Gen.Gen2)
            UsbId.Usb3G.usbFilter(vendorId, productId) -> UsbEcg(realDevice = this, gen = Gen.Gen3)
            else -> return null
        }
    }

data class UsbDeviceFilter(
    override val name: String = "Usb Ecg",
    override val nameMask: String = "",
    override val transmission: Transmission = Transmission.Usb,
    val usbIds: List<UsbId> = listOf(UsbId.Usb2G, UsbId.Usb3G)
) : DeviceFilter

class UsbDeviceDiscovery : DeviceDiscovery {
    companion object {
        private val USB_PERMISSION = "${globalContext.packageName}.USB_PERMISSION"
    }

    private val innerScope = CoroutineScope(Dispatchers.Default)

    private val usbManager by lazy {
        globalContext.getSystemService(UsbManager::class.java)
    }

    private val _scanResultFlow = MutableStateFlow<Result<List<ECGDevice>>>(
        Result.success(
            emptyList()
        )
    )

    private var deviceFilters: List<DeviceFilter> = listOf()

    override val deviceFlow = _scanResultFlow

    override fun discover(
        scope: CoroutineScope?,
        timeoutMills: Long,
        vararg deviceFilters: DeviceFilter
    ) {
        this.deviceFilters = deviceFilters.asList()
        innerScope.launch {
            var device = pickDevice()
            if (device == null) device = waitForUsbAttachment()
            if (device == null) {
                DeviceLog.log("No usb device attached!")
                _scanResultFlow.tryEmit(Result.failure(Throwable("No usb device attached!")))
                return@launch
            }
            if (usbManager.hasPermission(device)) {
                device.asEcgDevice?.let { ecg ->
                    _scanResultFlow.tryEmit(Result.success(listOf(ecg)))
                }
                return@launch
            }

            device = requestPermission(device)
            if (device == null) {
                _scanResultFlow.tryEmit(Result.failure(Throwable("Usb permission denied")))
                return@launch
            }

            device.asEcgDevice?.let { ecg ->
                _scanResultFlow.tryEmit(Result.success(listOf(ecg)))
            }
        }
    }

    private fun pickDevice(): UsbDevice? {
        val devices = usbManager.deviceList
        if (devices.isNullOrEmpty()) {
            return null
        }
        var target: UsbDevice? = null
        devices.entries.forEach {
            val device = it.value
            val usbId = deviceFilters
                .filterIsInstance<UsbDeviceFilter>()
                .flatMap { filter -> filter.usbIds }
                .firstOrNull { usbId ->
                    usbId.usbFilter(
                        device.vendorId,
                        device.productId
                    ).also { matched -> if (matched) target = device }
                }
            if (usbId != null) {
                return@forEach
            }
        }
        return target
    }


    override fun stop(): Result<Unit> {
        return Result.success(Unit)
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private suspend fun waitForUsbAttachment(timeoutMills: Long = 5_000L): UsbDevice? =
        coroutineScope {
            var device: UsbDevice? = null
            var usbAttachmentReceiver: BroadcastReceiver? = null

            DeviceLog.log("Wait ${timeoutMills}ms for usb attachment")

            try {
                withTimeout(timeoutMills) {
                    suspendCancellableCoroutine { continuation ->
                        usbAttachmentReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                when (intent?.action) {
                                    ACTION_USB_DEVICE_ATTACHED -> {
                                        runCatching {
                                            usbAttachmentReceiver?.let {
                                                globalContext.unregisterReceiver(
                                                    it
                                                )
                                            }
                                        }
                                        device = pickDevice()
                                        DeviceLog.log("Usb attached: ${device?.deviceName}")
                                        if (continuation.isActive) {
                                            if (device != null) {
                                                continuation.resume(Unit)
                                            } else {
                                                continuation.cancel(CancellationException("Wait for attachment failed."))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                globalContext.registerReceiver(
                                    usbAttachmentReceiver,
                                    IntentFilter(ACTION_USB_DEVICE_ATTACHED),
                                    Context.RECEIVER_EXPORTED
                                )
                            } else {
                                globalContext.registerReceiver(
                                    usbAttachmentReceiver,
                                    IntentFilter(ACTION_USB_DEVICE_ATTACHED)
                                )
                            }
                        }

                        continuation.invokeOnCancellation {
                            DeviceLog.log("Coroutine was cancelled or timed out")
                            runCatching {
                                usbAttachmentReceiver?.let { globalContext.unregisterReceiver(it) }
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                DeviceLog.log("Timeout while waiting for USB attachment")
            } finally {
                runCatching {
                    usbAttachmentReceiver?.let { globalContext.unregisterReceiver(it) }
                }
            }

            DeviceLog.log("Waiting stopped: ${device?.deviceName}")
            device
        }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private suspend fun requestPermission(usbDevice: UsbDevice) =
        coroutineScope {
            val waitResult = CompletableDeferred<Unit>(parent = coroutineContext.job)
            var device: UsbDevice? = null
            var usbPermissionReceiver: BroadcastReceiver? = null
            DeviceLog.log("Request usb permission")

            usbPermissionReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        USB_PERMISSION -> {
                            runCatching {
                                usbPermissionReceiver.let { globalContext.unregisterReceiver(it) }
                            }
                            device = pickDevice()
                            DeviceLog.log(
                                "Usb permission request result:${
                                    device?.let {
                                        usbManager.hasPermission(
                                            it
                                        )
                                    } ?: false
                                }")
                            if (waitResult.isActive) {
                                if (device != null)
                                    waitResult.complete(Unit)
                                else waitResult.cancel("Wait for attachment failed.")
                            }
                        }
                    }
                }
            }

            runCatching {
                usbPermissionReceiver.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        globalContext.registerReceiver(
                            it,
                            IntentFilter(USB_PERMISSION),
                            Context.RECEIVER_EXPORTED
                        )
                    } else {
                        globalContext.registerReceiver(it, IntentFilter(USB_PERMISSION))
                    }
                }
            }
            usbManager.requestPermission(
                usbDevice,
                PendingIntent.getBroadcast(
                    globalContext,
                    0,
                    Intent(USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            waitResult.await() // Wait for user permission

            if (device != null && usbManager.hasPermission(device)) device else null
        }
}