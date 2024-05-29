package android.boot.device.ecg.ble

import android.boot.device.api.Device
import androidx.bluetooth.BluetoothDevice

object BleDeviceFactory {
    fun createBleDevice(
        bluetoothDevice: BluetoothDevice,
        mac: String,
        vararg scanFilters: String
    ): Device<BluetoothDevice>? {
        return when {
            scanFilters.first() == "WWKECG12E" -> BleEcg3G(bluetoothDevice, mac)
            else -> null
        }
    }
}