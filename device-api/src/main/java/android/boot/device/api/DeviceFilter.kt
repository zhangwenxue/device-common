package android.boot.device.api

interface DeviceFilter {
    val name: String
    val nameMask: String
    val transmission: Transmission
}