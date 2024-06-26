package android.boot.device.ecg.nativeble

import android.annotation.SuppressLint
import android.boot.common.provider.globalContext
import android.boot.device.api.Channel
import android.boot.device.api.Connection
import android.boot.device.api.State
import android.content.pm.PackageManager
import android.util.Log
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.GattCharacteristic
import androidx.bluetooth.GattClientScope
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.CountDownLatch

fun hasNoBluetoothConnectPermission(): Boolean {
    return ActivityCompat.checkSelfPermission(
        globalContext,
        "android.permission.BLUETOOTH_CONNECT"
    ) != PackageManager.PERMISSION_GRANTED
}

fun assertBluetoothConnectPermission() {
    if (hasNoBluetoothConnectPermission()) Log.e(
        "_BleConnection",
        "android.permission.BLUETOOTH_CONNECT permission not granted!"
    )
}


class CharacteristicChannel(
    override val id: String,
    override val name: String?,
    private val gattScope: GattClientScope? = null,
    private val serviceUUid: String,
    private val characterUUid: String,
) : Channel {
    private var characteristic: GattCharacteristic? = null

    override suspend fun read(src: ByteArray?, timeoutMillis: Int): Result<ByteArray> {
        return getCharacteristic()?.let { gattScope?.readCharacteristic(it) }
            ?: Result.failure(Throwable("Invalid characteristic"))
    }


    override suspend fun write(dest: ByteArray, timeoutMillis: Int): Result<Unit> {
        return runCatching {
            getCharacteristic()?.let { gattScope?.writeCharacteristic(it, dest) }
                ?: Result.failure(
                    Throwable("Invalid characteristic")
                )
        }
    }

    override suspend fun listen(): Flow<Result<ByteArray>> {
        return getCharacteristic()?.let {
            gattScope?.subscribeToCharacteristic(it)?.map { array ->
                Log.i("_BleConnection", "map:${array.joinToString { "%02X".format(it) }}")
                Result.success(array)
            }
        } ?: flowOf(Result.failure(Throwable("Invalid characteristic")))
    }

    private fun getCharacteristic(): GattCharacteristic? {
        return characteristic ?: gattScope
            ?.getService(UUID.fromString(serviceUUid))
            ?.getCharacteristic(UUID.fromString(characterUUid))?.apply {
                characteristic = this
            }
    }
}

abstract class BleConnection(
    override val name: String,
    override val realDevice: BluetoothDevice,
    private val eventFlow: MutableStateFlow<State>,
) : Connection {
    private val bluetoothLe by lazy {
        BluetoothLe(globalContext)
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var gattScope: GattClientScope? = null

    private val mutex = Mutex()

    private var job: CompletableDeferred<Unit>? = null


    @SuppressLint("MissingPermission")

    override suspend fun connect(): Result<Unit> = mutex.withLock {
        if (gattScope != null) {
            eventFlow.update { State.Connected }
            return Result.success(Unit)
        }
        return withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            eventFlow.update { State.Connecting }
            bluetoothLe.connectGatt(realDevice) {
                gattScope = this
                latch.countDown()
                job = CompletableDeferred()
                job?.await()
            }
            latch.await()
            if (gattScope == null) {
                eventFlow.update { State.Disconnected }
                Result.failure(RuntimeException("Connect failed"))
            } else {
                eventFlow.update { State.Connected }
                Result.success(Unit)
            }
        }
    }


    override fun disconnect() {
        gattScope = null
        job?.complete(Unit)
        job = null
        bluetoothLe.close()
        eventFlow.update { State.Disconnected }
    }


    internal fun createCharacteristicChannel(
        serviceUUid: String,
        characterUUid: String,
        order: Int = 0
    ): Channel {
        return CharacteristicChannel(
            id = "$order",
            name = "Channel-$order",
            gattScope,
            serviceUUid,
            characterUUid
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            Log.i(
                "_BleConnection:",
                "Connect Gatt called${Thread.currentThread().name}:${realDevice.name}"
            )
            val completableDeferred = CompletableDeferred<Result<Unit>>()

            scope.launch {
                bluetoothLe.connectGatt(realDevice) {
                    gattScope = this
                    completableDeferred.complete(Result.success(Unit))
                }
            }
            completableDeferred.await()
        }
    }
}