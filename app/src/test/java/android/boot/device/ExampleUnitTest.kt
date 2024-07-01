package android.boot.device

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.experimental.xor

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun parseSN() {
        val bytes = byteArrayOf(
            0xa5.toByte(),
            0x06,
            0x15,
            0x32,
            0x30,
            0x32,
            0x20,
            0x23,
            0x21,
            0x23,
            0x25,
            0x3c,
            0x21,
            0x0c,
            0x4b,
            0x4b,
            0x23,
            0x22,
            0x32,
            0x4b,
            0x21,
            0x21,
            0x21,
            0x21,
            0x6e,
            0x5a
        )

        val bs = bytes.sliceArray(3 + 4 until bytes.size - 2)
        val dataArray = bs.map { ch ->
            ch xor 0x11
        }.toByteArray()

        val sn = String(dataArray)
        println("SN is:\n$sn")
    }
}