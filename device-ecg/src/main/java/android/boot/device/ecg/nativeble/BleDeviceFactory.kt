package android.boot.device.ecg.nativeble

import android.boot.device.api.ECGDevice
import androidx.bluetooth.BluetoothDevice

object BleDeviceFactory {
    fun createBleDevice(
        bluetoothDevice: BluetoothDevice,
        mac: String
    ): ECGDevice? {
        return when {
            bluetoothDevice.name == "WWKECG12E" -> BleEcg3G(bluetoothDevice, mac)
            else -> BleEcg3G(bluetoothDevice, mac)
        }
    }
}