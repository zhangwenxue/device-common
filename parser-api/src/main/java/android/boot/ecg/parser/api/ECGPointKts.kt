package android.boot.ecg.parser.api

typealias ECGPoints = List<Result<ECGPoint>>

sealed class Lead(val name: String, val value: Int) {
    data class I(val data: Int) :
        Lead("I", data)

    data class II(val data: Int) :
        Lead("II", data)

    data class III(val data: Int) :
        Lead("III", data)

    data class AVR(val data: Int) :
        Lead("aVR", data)

    data class AVL(val data: Int) :
        Lead("aVL", data)

    data class AVF(val data: Int) :
        Lead("aVF", data)

    data class V1(val data: Int) :
        Lead("V1", data)

    data class V2(val data: Int) :
        Lead("V2", data)

    data class V3(val data: Int) :
        Lead("V3", data)

    data class V4(val data: Int) :
        Lead("V4", data)

    data class V5(val data: Int) :
        Lead("V5", data)

    data class V6(val data: Int) :
        Lead("V6", data)
}

sealed class Electrode(val name: String, val electrodeOff: Boolean) {
    data class LA(val off: Boolean) : Electrode("LA", off)
    data class RA(val off: Boolean) : Electrode("RA", off)
    data class LL(val off: Boolean) : Electrode("LL", off)
    data class RL(val off: Boolean) : Electrode("RL", off)
    data class V1(val off: Boolean) : Electrode("V1", off)
    data class V2(val off: Boolean) : Electrode("V2", off)
    data class V3(val off: Boolean) : Electrode("V3", off)
    data class V4(val off: Boolean) : Electrode("V4", off)
    data class V5(val off: Boolean) : Electrode("V5", off)
    data class V6(val off: Boolean) : Electrode("V6", off)
}

data class ECGPoint(
    val packageNo: Int,
    val leads: List<Lead>,
    val electrodes: List<Electrode>,
    val peace: Boolean,
)
