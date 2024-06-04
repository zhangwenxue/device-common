package android.boot.device.ecg.ble

import android.boot.device.api.Device
import androidx.bluetooth.BluetoothDevice

object BleDeviceFactory {
    fun createBleDevice(
        bluetoothDevice: BluetoothDevice,
        mac: String
    ): Device<BluetoothDevice>? {
        return when {
            bluetoothDevice.name == "WWKECG12E" -> BleEcg3G(bluetoothDevice, mac)
            else -> BleEcg3G(bluetoothDevice, mac)
        }
    }
}