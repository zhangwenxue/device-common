package android.boot.ecg.parser.api

import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun parse_isCorrect() {
        val data = byteArrayOf(
            0x84.toByte(),
            0x0,
            0x14,
            0x2,
            0x19,
            0x02,
            0x30,
            0xf,
            0x0c,
            0x1f,
            0x1,
            0x6,
            0xf,
            0x7,
            0x5,
            0x2,
            0x7f,
            0x30,
            0x11,
            0x2f,
            0x27,
            0x32,
            0x0f,
            0x14,
            0x42,
            0x01,
            0x08,
            0xff.toByte(),
            0x7f,
            0x1d,
            0x51
        )

        val ret1 = ParseOriginEcgData().parseEcgData(data, data.size)
        val ret2 = ECG3UsbParser().parseEcgData(data)

        println(ret1.leadsData.joinToString())
        println(ret2.leadsData.joinToString())
        println("/n------------------------/n")
        println(ret1.leadsOffState.joinToString())
        println(ret2.leadsOffState.joinToString())
        println("/n------------------------/n")
        println("${ret1.packageId} | ${ret1.peace}")
        println("${ret2.packageId} | ${ret2.peace}")
    }

    @Test
    fun test_slice() {
        val bytes = ByteArray(62) { it.toByte() }
        for (i in bytes.indices step 31) {
            val segment = bytes.sliceArray(i until i + 31)
            println("segment:${segment.joinToString()}")
        }
    }
}