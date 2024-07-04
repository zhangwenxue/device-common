package android.boot.device.ecg.usb

import android.boot.device.api.ECGCommands
import android.boot.device.ecg.usb.UsbCommands.USB_READ_SN_CMD
import android.boot.device.ecg.usb.UsbCommands.USB_READ_VERSION_CMD
import android.boot.device.ecg.usb.UsbCommands.USB_START_COLLECT_CMD
import android.boot.device.ecg.usb.UsbCommands.USB_STOP_COLLECT_CMD
import android.boot.device.ecg.util.ECG3GenParser.packWriteSNCmd
import android.boot.device.ecg.util.ECG3GenParser.parseSN
import android.boot.device.ecg.util.ECG3GenParser.parseVersion

internal object UsbCommands {
    internal val USB_READ_VERSION_CMD = byteArrayOf(0xA5.toByte(), 0x05, 0x00, 0x05, 0x5A)
    internal val USB_START_COLLECT_CMD = byteArrayOf(0xA5.toByte(), 0x03, 0x00, 0x03, 0x5A)
    internal val USB_STOP_COLLECT_CMD = byteArrayOf(0xA5.toByte(), 0x04, 0x00, 0x04, 0x5A)
    internal val USB_READ_SN_CMD = byteArrayOf(0xA5.toByte(), 0x06, 0x00, 0x06, 0x5A)
}

class Usb3GenCommands(
    override val readSN: ByteArray = USB_READ_SN_CMD,
    override val readVersion: ByteArray = USB_READ_VERSION_CMD,
    override val startCollect: ByteArray = USB_START_COLLECT_CMD,
    override val stopCollect: ByteArray = USB_STOP_COLLECT_CMD,
) : ECGCommands {
    override val versionParser: (ByteArray) -> Result<String> = { parseVersion(it) }
    override val snParser: (ByteArray) -> Result<String> = { parseSN(it) }
    override val snPackager: (String) -> Result<ByteArray> = { packWriteSNCmd(it) }
}