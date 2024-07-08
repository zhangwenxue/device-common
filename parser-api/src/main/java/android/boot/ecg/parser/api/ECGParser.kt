package android.boot.ecg.parser.api

interface ECGParser {
    fun parse(bytes: ByteArray): ECGPoints
}