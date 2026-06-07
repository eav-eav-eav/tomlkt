package dev.eav.tomlkt

import kotlinx.serialization.Serializable
import kotlin.test.Test

class NestTest {
    @Serializable
    data class M1(
        val s: String,
        val clm: Map<String, List<C1>>
    )

    @Serializable
    data class C1(
        val s: String
    )

    val m11 = M1(
        s = "1",
        clm = mapOf(
            "1" to listOf(
                C1(
                    s = "1"
                )
            )
        )
    )

    val s11 = """
        s = "1"
        
        [clm]
        
        [[clm.1]]
        s = "1"
    """.trimIndent()

    @Test
    fun encodeDeeplyNested1() {
        testEncode(M1.serializer(), m11, s11)
    }

    @Test
    fun decodeDeeplyNested1() {
        testDecode(M1.serializer(), s11, m11)
    }

    val m12 = M1(
        s = "1",
        clm = mapOf(
            "1" to emptyList()
        )
    )

    val s12 = """
        s = "1"
        
        [clm]
        1 = [  ]
    """.trimIndent()

    @Test
    fun encodeDeeplyNested2() {
        testEncode(M1.serializer(), m12, s12)
    }

    @Test
    fun decodeDeeplyNested2() {
        testDecode(M1.serializer(), s12, m12)
    }

    val m13 = M1(
        s = "1",
        clm = emptyMap()
    )

    val s13 = """
        s = "1"
        clm = {  }
    """.trimIndent()

    @Test
    fun encodeDeeplyNested3() {
        testEncode(M1.serializer(), m13, s13)
    }

    @Test
    fun decodeDeeplyNested3() {
        testDecode(M1.serializer(), s13, m13)
    }

    @Serializable
    data class M2(
        val clm: Map<String, List<C1>>,
        val s: String
    )

    val m21 = M2(
        clm = mapOf(
            "1" to listOf(
                C1(
                    s = "1"
                )
            )
        ),
        s = "1"
    )

    val s21 = """
        s = "1"
        
        [clm]
        
        [[clm.1]]
        s = "1"
    """.trimIndent()

    @Test
    fun encodeDeeplyNested4() {
        testEncode(M2.serializer(), m21, s21)
    }

    @Test
    fun decodeDeeplyNested4() {
        testDecode(M2.serializer(), s21, m21)
    }

    val m22 = M2(
        clm = mapOf(
            "1" to emptyList()
        ),
        s = "1"
    )

    val s22 = """
        s = "1"
        
        [clm]
        1 = [  ]
    """.trimIndent()

    @Test
    fun encodeDeeplyNested5() {
        testEncode(M2.serializer(), m22, s22)
    }

    @Test
    fun decodeDeeplyNested5() {
        testDecode(M2.serializer(), s22, m22)
    }

    val m23 = M2(
        clm = emptyMap(),
        s = "1"
    )

    val s23 = """
        clm = {  }
        s = "1"
    """.trimIndent()

    @Test
    fun encodeDeeplyNested6() {
        testEncode(M2.serializer(), m23, s23)
    }

    @Test
    fun decodeDeeplyNested6() {
        testDecode(M2.serializer(), s23, m23)
    }

    @Serializable
    data class M3(
        val s: String,
        val clm: Map<String, List<C2>>
    )

    @Serializable
    class C2 {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is C2) return false
            return true
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }
    }

    val m31 = M3(
        s = "A",
        clm = mapOf(
            "O" to listOf(
                C2()
            )
        )
    )

    val s31 = """
        s = "A"
        
        [clm]
        
        [[clm.O]]
        
    """.trimIndent()

    @Test
    fun encodeDeeplyNested7() {
        testEncode(M3.serializer(), m31, s31)
    }

    @Test
    fun decodeDeeplyNested7() {
        testDecode(M3.serializer(), s31, m31)
    }

    val m32 = M3(
        s = "A",
        clm = mapOf(
            "O" to emptyList()
        )
    )

    val s32 = """
        s = "A"
        
        [clm]
        O = [  ]
    """.trimIndent()

    @Test
    fun encodeDeeplyNested8() {
        testEncode(M3.serializer(), m32, s32)
    }

    @Test
    fun decodeDeeplyNested8() {
        testDecode(M3.serializer(), s32, m32)
    }

    val m33 = M3(
        s = "A",
        clm = emptyMap()
    )

    val s33 = """
        s = "A"
        clm = {  }
    """.trimIndent()

    @Test
    fun encodeDeeplyNested9() {
        testEncode(M3.serializer(), m33, s33)
    }

    @Test
    fun decodeDeeplyNested9() {
        testDecode(M3.serializer(), s33, m33)
    }

    @Serializable
    data class M4(
        val clm: Map<String, List<C2>>,
        val s: String
    )

    val m41 = M4(
        clm = mapOf(
            "1" to listOf(
                C2()
            )
        ),
        s = ""
    )

    val s41 = """
        s = ""
        
        [clm]
        
        [[clm.1]]
        
    """.trimIndent()

    @Test
    fun encodeDeeplyNested10() {
        testEncode(M4.serializer(), m41, s41)
    }

    @Test
    fun decodeDeeplyNested10() {
        testDecode(M4.serializer(), s41, m41)
    }

    val m42 = M4(
        clm = mapOf(
            "1" to emptyList()
        ),
        s = ""
    )

    val s42 = """
        s = ""
        
        [clm]
        1 = [  ]
    """.trimIndent()

    @Test
    fun encodeDeeplyNested11() {
        testEncode(M4.serializer(), m42, s42)
    }

    @Test
    fun decodeDeeplyNested11() {
        testDecode(M4.serializer(), s42, m42)
    }

    val m43 = M4(
        clm = emptyMap(),
        s = ""
    )

    val s43 = """
        clm = {  }
        s = ""
    """.trimIndent()

    @Test
    fun encodeDeeplyNested12() {
        testEncode(M4.serializer(), m43, s43)
    }

    @Test
    fun decodeDeeplyNested12() {
        testDecode(M4.serializer(), s43, m43)
    }

    @Serializable
    data class M5(
        val cs: List<C3>
    )

    @Serializable
    data class C3(
        val c: C3?
    )

    val m51 = M5(
        cs = listOf(
            C3(
                c = C3(
                    c = null
                )
            )
        )
    )

    val s51 = """
        [[cs]]
        
        [cs.c]
        c = null
    """.trimIndent()

    @Test
    fun encodeDeeplyNested13() {
        testEncode(M5.serializer(), m51, s51)
    }

    @Test
    fun decodeDeeplyNested13() {
        testDecode(M5.serializer(), s51, m51)
    }

    val m52 = M5(
        cs = listOf(
            C3(
                c = null
            )
        )
    )

    val s52 = """
        [[cs]]
        c = null
    """.trimIndent()

    @Test
    fun encodeDeeplyNested14() {
        testEncode(M5.serializer(), m52, s52)
    }

    @Test
    fun decodeDeeplyNested14() {
        testDecode(M5.serializer(), s52, m52)
    }
}
