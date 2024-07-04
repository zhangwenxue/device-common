package android.boot.device.api

interface ECGCommands {
    val readSN: ByteArray
    val readVersion: ByteArray
    val startCollect: ByteArray
    val stopCollect: ByteArray
    val versionParser: (ByteArray) -> Result<String>
    val snParser: (ByteArray) -> Result<String>
    val snPackager: (String) -> Result<ByteArray>
}