package android.boot.device.ecg.util

import android.boot.common.extensions.asHexString
import android.boot.device.api.DeviceLog
import kotlin.experimental.xor

object ECG3GenParser {
    fun packWriteSNCmd(sn: String): Result<ByteArray> {
        if (sn.trimIndent().isBlank()) return Result.failure(Throwable("Invalid sn:$sn"))
        val data = sn.toByteArray(Charsets.UTF_8)
        val ret = ByteArray(5 + data.size)
        ret[0] = (0xA5).toByte()
        ret[1] = (0x02).toByte()
        ret[2] = (data.size).toByte()
        data.copyInto(ret, 3)
        val subArray = ret.sliceArray(1 until data.size + 3)
        ret[3 + data.size] = subArray.xorReduce()
        ret[ret.size - 1] = (0x5A).toByte()
        return Result.success(ret)
    }


    fun parseSN(data: ByteArray): Result<String> {
        if (data.size < 5) return Result.failure(Throwable("Invalid data size:${data.size}"))
        return if (data[0] == (0xA5).toByte() && data[1] == (0x06).toByte()) {
            val length = data[2].toInt()
            val snArray = ByteArray(length + 5)
            System.arraycopy(data, 0, snArray, 0, length + 5)
            /*
            *val checkSum = snArray[length + 2]
            val checkSumArray = snArray.sliceArray(1 until length + 3)
            val xorReduce = checkSumArray.xorReduce()
            if (xorReduce != checkSum) {
                DeviceLog.log(
                    "校验和失败:${
                        snArray.joinToString { ch ->
                            String.format(
                                "%02x",
                                ch
                            )
                        }
                    }"
                )
                return Result.failure(Throwable("校验和失败"))
            }*/
            DeviceLog.log("SN-ORIGIN:\n${snArray.asHexString()}")

            val realDataArray = snArray.sliceArray(3 + 4 until length + 3)
            DeviceLog.log(
                "REAL-SN:\n${
                    realDataArray.asHexString()
                }"
            )

            val finalDataArray = realDataArray.map { ch -> ch xor 0x11 }.toByteArray().also {
                DeviceLog.log("FINAL-SN:\n${it.asHexString()}")
            }

            Result.success(String(finalDataArray, Charsets.UTF_8))
        } else {
            Result.failure(
                RuntimeException(
                    "Invalid sn response:${
                        data.asHexString()
                    }"
                )
            )
        }
    }

    fun parseVersion(data: ByteArray): Result<String> {
        return if (data[0] == (0xA5).toByte() && data[1] == (0x05).toByte()) {
            val length = data[2].toInt()
            val snArray = ByteArray(length)
            System.arraycopy(data, 3, snArray, 0, length)
            Result.success(String(snArray, Charsets.UTF_8))
        } else {
            Result.failure(
                RuntimeException(
                    "Invalid version response:${data.asHexString()}"
                )
            )
        }
    }

    private fun ByteArray.xorReduce() = reduce { acc, byteValue -> acc xor byteValue }


}