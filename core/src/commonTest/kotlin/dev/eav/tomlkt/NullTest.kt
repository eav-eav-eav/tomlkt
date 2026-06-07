package dev.eav.tomlkt

import kotlinx.serialization.Serializable
import kotlin.test.Test

class NullTest {
    @Serializable
    data class M1(
        val ni: Int?
    )

    val m11 = M1(
        ni = null
    )

    val s11 = """
        ni = null
    """.trimIndent()

    @Test
    fun encodeRegularly() {
        testEncode(M1.serializer(), m11, s11)
    }

    @Test
    fun decodeRegularly() {
        testDecode(M1.serializer(), s11, m11)
    }

    @Serializable
    data class M2(
        val nss: List<String?>
    )
    
    val m21 = M2(
        nss = listOf(
            "a",
            null
        )
    )
    
    val s21 = """
        nss = [
            "a",
            null
        ]
    """.trimIndent()
    
    @Test
    fun encodeInsideBlockArray() {
        testEncode(M2.serializer(), m21, s21)
    }

    @Test
    fun decodeInsideBlockArray() {
        testDecode(M2.serializer(), s21, m21)
    }

    @Serializable
    data class M3(
        val nc: C?,
        val c: C
    )

    @Serializable
    data class C(
        val i: Int
    )

    val m31 = M3(
        nc = null,
        c = C(
            i = 1
        )
    )

    val s31 = """
        nc = null
        
        [c]
        i = 1
    """.trimIndent()

    @Test
    fun encodeNullableTableWhenNull() {
        testEncode(M3.serializer(), m31, s31)
    }

    @Test
    fun decodeNullableTableWhenNull() {
        testDecode(M3.serializer(), s31, m31)
    }

    val m32 = M3(
        nc = C(
            i = -1
        ),
        c = C(
            i = 0
        )
    )

    val s32 = """
        [nc]
        i = -1
        
        [c]
        i = 0
    """.trimIndent()

    @Test
    fun encodeNullableTableWhenNotNull() {
        testEncode(M3.serializer(), m32, s32)
    }

    @Test
    fun decodeNullableTableWhenNotNull() {
        testDecode(M3.serializer(), s32, m32)
    }

    val s33 = """
        [c]
        i = 1
    """.trimIndent()

    val t1 = Toml { explicitNulls = false }

    @Test
    fun encodeWithoutExplicitNulls() {
        testEncode(M3.serializer(), m31, s33, t1)
    }

    @Test
    fun decodeWithoutExplicitNulls() {
        testDecode(M3.serializer(), s33, m31, t1)
    }
}
