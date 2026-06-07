package dev.eav.tomlkt

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.test.Test

@OptIn(ExperimentalUnsignedTypes::class)
class ValueClassTest {
    @Serializable
    data class M1(
        val v: V1
    )

    @Serializable
    @JvmInline
    value class V1(val i: Int)

    val m11 = M1(
        v = V1(
            i = 0
        )
    )

    val s11 = """
        v = 0
    """.trimIndent()

    @Test
    fun encodePrimitiveBacked() {
        testEncode(M1.serializer(), m11, s11)
    }

    @Test
    fun decodePrimitiveBacked() {
        testDecode(M1.serializer(), s11, m11)
    }

    @Serializable
    data class M2(
        val v: V2
    )

    @Serializable
    @JvmInline
    value class V2(val c: C)

    @Serializable
    data class C(val i: Int)

    val m21 = M2(
        v = V2(
            c = C(
                i = 0
            )
        )
    )

    val s21 = """
        [v]
        i = 0
    """.trimIndent()

    @Test
    fun encodeClassBacked() {
        testEncode(M2.serializer(), m21, s21)
    }

    @Test
    fun decodeClassBacked() {
        testDecode(M2.serializer(), s21, m21)
    }

    @Serializable
    data class M3(
        val v: V3
    )

    @Serializable
    @JvmInline
    value class V3(val ns: String?)

    val m31 = M3(
        v = V3(null)
    )

    val s31 = """
        v = null
    """.trimIndent()

    @Test
    fun encodeNullableBacked() {
        testEncode(M3.serializer(), m31, s31)
    }

    @Test
    fun decodeNullableBacked() {
        testDecode(M3.serializer(), s31, m31)
    }

    @Serializable
    data class M4(
        val u: UByte
    )

    val m41 = M4(
        u = 255u
    )

    val s41 = """
        u = 255
    """.trimIndent()

    @Test
    fun encodeUnsigned() {
        testEncode(M4.serializer(), m41, s41)
    }

    @Test
    fun decodeUnsigned() {
        testDecode(M4.serializer(), s41, m41)
    }

    @Serializable
    class M5(
        val ua: UByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is M5) return false
            if (!ua.contentEquals(other.ua)) return false
            return true
        }

        override fun hashCode(): Int {
            return ua.contentHashCode()
        }
    }

    val m51 = M5(
        ua = ubyteArrayOf(255u, 1u, 0u)
    )

    val s51 = """
        ua = [
            255,
            1,
            0
        ]
    """.trimIndent()

    @Test
    fun encodeUnsignedArray() {
        testEncode(M5.serializer(), m51, s51)
    }

    @Test
    fun decodeUnsignedArray() {
        testDecode(M5.serializer(), s51, m51)
    }
}
