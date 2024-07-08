package android.boot.ecg.parser.api.builtin

import android.boot.ecg.parser.api.ECGParser
import android.boot.ecg.parser.api.ECGPoint
import android.boot.ecg.parser.api.ECGPoints
import android.boot.ecg.parser.api.Electrode
import android.boot.ecg.parser.api.Lead
import android.util.Log

class ECG3GenUsbParser : ECGParser {
    companion object {
        const val PACK_DATA_LEN = 31
    }

    override fun parse(bytes: ByteArray): ECGPoints {
        if (bytes.size % 31 != 0) {
            return listOf(Result.failure(IllegalArgumentException("The length of the byte array must be a multiple of 31.")))
        }
        val result = mutableListOf<Result<ECGPoint>>()
        for (i in bytes.indices step 31) {
            val segment = bytes.sliceArray(i until i + 31)
            result.add(parseEcgData(segment))
        }
        return result.toList()
    }

    private fun parseEcgData(buffer: ByteArray): Result<ECGPoint> = runCatching {
        if (((buffer[0].toInt()) and 0x80) == 0x80) {
            error(String.format("_invalid Header%02x", buffer[0]))
        }
        val bitData = BooleanArray(PACK_DATA_LEN - 4)
        val leadData = ByteArray(3)
        val packetEcgData = ByteArray(24)
        val leadState = BooleanArray(9)
        convertToBits(buffer, bitData)
        getLeadStateOriginalData(buffer, bitData, leadData)
        val packageId = leadData[0].toInt()
        getLeadEcgOriginalData(buffer, bitData, packetEcgData)
        val pace = getEcgPaceData(leadData)
        getEcgLeadValue(leadData, leadState)
        val rawEcgPoints = packetEcgData.parseToEcgPoints()
        val targetEcgPoints = parseEcgPoints(rawEcgPoints.normalize())

        val electrodes = leadState.mapIndexed { idx, off ->
            when (idx) {
                0 -> Electrode.V3(off)
                1 -> Electrode.V4(off)
                2 -> Electrode.V5(off)
                3 -> Electrode.V6(off)
                4 -> Electrode.RA(off)
                5 -> Electrode.LA(off)
                6 -> Electrode.LL(off)
                7 -> Electrode.V1(off)
                8 -> Electrode.V2(off)
                else -> null
            }
        }.filterNotNull().toMutableList().apply {
            add(Electrode.RL(false)) //Note RL electrode is always consider to be connected
            toList()
        }

        val leads = targetEcgPoints.mapIndexed { idx, value ->
            when (idx) {
                0 -> Lead.I(value)
                1 -> Lead.II(value)
                2 -> Lead.III(value)
                3 -> Lead.AVR(value)
                4 -> Lead.AVL(value)
                5 -> Lead.AVF(value)
                6 -> Lead.V1(value)
                7 -> Lead.V2(value)
                8 -> Lead.V3(value)
                9 -> Lead.V4(value)
                10 -> Lead.V5(value)
                11 -> Lead.V6(value)
                else -> {
                    Log.e("_ECG3GenUsbParser", "Invalid ecgPoints")
                    null
                }
            }
        }.filterNotNull()

        ECGPoint(packageId, leads, electrodes, pace)
    }

    private fun convertToBits(buffer: ByteArray, bitData: BooleanArray) {
        var cnt = 0
        for (j in 0 until 4) {
            val bitNum = if (j == 0) 5 else 6
            for (i in bitNum downTo 0) {
                bitData[cnt++] = (buffer[j].toInt() shr i and 1) == 1
            }
        }
    }

    private fun getLeadStateOriginalData(
        buffer: ByteArray,
        bitData: BooleanArray,
        leadData: ByteArray
    ) {
        for (i in 0 until 3) {
            leadData[i] = if (bitData[i]) {
                (buffer[i + 4].toInt() or 0x80).toByte()
            } else {
                buffer[i + 4]
            }
        }
    }

    private fun getLeadEcgOriginalData(
        buffer: ByteArray,
        bitData: BooleanArray,
        ecgData: ByteArray
    ) {
        for (i in 0 until 24) {
            ecgData[i] = if (bitData[i + 3]) {
                (buffer[i + 7].toInt() or 0x80).toByte()
            } else {
                buffer[i + 7]
            }
        }
    }

    private fun getEcgPaceData(leadData: ByteArray): Boolean {
        return (leadData[2].toInt() and 0x01) == 1
    }

    private fun getEcgLeadValue(leadData: ByteArray, leadState: BooleanArray) {
        var cnt = 0
        for (i in 0 until 2) {
            for (j in 0 until (5 - i)) {
                leadState[cnt++] = (leadData[i + 1].toInt() shr (i * 4 + j) and 1) == 1
            }
        }
    }

    private fun ByteArray.parseToEcgPoints(): IntArray {
        val ecgData = IntArray(size / 3)
        var index = 0
        var i = 0
        while (i < size) {
            ecgData[index++] = (this[i++].toInt() and 0xFF shl 16) or
                    (this[i++].toInt() and 0xFF shl 8) or
                    (this[i++].toInt() and 0xFF)
        }
        return ecgData
    }


    private fun IntArray.normalize(): IntArray {
        return IntArray(this.size) { index ->
            val value = this[index]
            if (value > 0x800000) value - 0x1000000 else value
        }
    }


    private fun parseEcgPoints(ecgData: IntArray): IntArray {
        return IntArray(12).apply {
            val (i, ii) = ecgData
            this[0] = i
            this[1] = ii
            this[2] = ii - i // III = II - I
            this[3] = -((ii + i) shr 1) // avR
            this[4] = i - (ii shr 1) // avL
            this[5] = ii - (i shr 1) // avF
            for (index in 2 until 8) {
                this[index + 4] = ecgData[index]
            }
        }
    }
}

