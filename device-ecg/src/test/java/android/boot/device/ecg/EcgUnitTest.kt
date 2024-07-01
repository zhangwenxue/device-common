package android.boot.device.ecg

import android.boot.device.ecg.usb.UsbEcg.Companion.READ_SN_CMD
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset
import kotlin.experimental.xor

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class EcgUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun xor_Test() {
        val bytes = byteArrayOf(
            0x43,
            0x31,
            0x32,
            0x32,
            0x30,
            0x32,
            0x33,
            0x30,
            0x33,
            0x30,
            0x34,
            0x30,
            0x30,
            0x30,
            0x30,
            0x30,
            0x30
        )
        val bytes2 = byteArrayOf(
            0x02,
            0x11,
            0x43,
            0x31,
            0x32,
            0x32,
            0x30,
            0x32,
            0x33,
            0x30,
            0x33,
            0x30,
            0x34,
            0x30,
            0x30,
            0x30,
            0x30,
            0x30,
            0x30
        )

//        val value = String(bytes)
//        val xor = bytes2.reduce { acc, byte -> acc xor byte }
//        println("value:$value\nxor:$xor")
//
//        val cmd = packWriteSNCommand("C1220230304000000")
//        println("cmd:${cmd.joinToString { String.format("%02x", it) }}")

        val bytess = byteArrayOf(/*0x06,15,*//*0x43,*//*0x31*//*0x32,*//*0x20,*/0x52,0x20,0x23,0x23,0x21,0x23,0x22,0x21,0x22,0x21,0x25,0x21,0x21,0x21,0x21,0x21,0x21).map { it xor 0x11 }
        val sn =
            byteArrayOf(
                0x06,
                0x15/*,0x43,0x31,0x32,0x20,0x52,0x20,*/,
                0x23,
                0x23,
                0x21,
                0x23,
                0x22,
                0x21,
                0x22,
                0x21,
                0x25,
                0x21,
                0x21,
                0x21,
                0x21,
                0x21,
                0x21
            ).reduce { acc, byte -> acc xor byte }
        println("SNXOR:$sn\n${String(bytess.toByteArray(),Charsets.UTF_8)}")
    }

    @Test
    fun sn_isCorrect() {
        val bytes = byteArrayOf(
            0xa5.toByte(),
            0x06,
            0x15,
            0x43,
            0x31,
            0x32,
            0x20,
            0x52,
            0x20,
            0x23,
            0x23,
            0x21,
            0x23,
            0x22,
            0x21,
            0x22,
            0x21,
            0x25,
            0x21,
            0x21,
            0x21,
            0x21,
            0x21,
            0x21,
            0x26,
            0x5a,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00
        )


        if (bytes[0] == (0xA5).toByte() && bytes[1] == READ_SN_CMD[1]) {
            val length = bytes[2].toInt()
            val targetBytes = ByteArray(length)
            System.arraycopy(bytes, 3, targetBytes, 0, length)
            println("SN: ${String(targetBytes, Charset.defaultCharset())}")
        } else {
            println("Error")
        }

    }

    private fun packWriteSNCommand(serial: String): ByteArray {
        val data = serial.toByteArray(Charsets.UTF_8)
        val ret = ByteArray(5 + data.size)
        ret[0] = (0xA5).toByte()
        ret[1] = (0x02).toByte()
        ret[2] = (data.size).toByte()
        data.copyInto(ret, 3)
        val subArray = ret.sliceArray(1 until data.size + 3)
        ret[3 + data.size] = subArray.reduce { acc, byteValue -> acc xor byteValue }
        ret[ret.size - 1] = (0x5A).toByte()
        return ret
    }
}