package dev.eav.tomlkt

import kotlinx.serialization.Serializable
import kotlin.test.Test

class CommentTest {
    @Serializable
    data class M1(
        @TomlComment("single")
        val i: Int
    )

    val m11 = M1(
        i = 0
    )

    val s11 = """
        # single
        i = 0
    """.trimIndent()

    @Test
    fun encodeSingleLine() {
        testEncode(M1.serializer(), m11, s11)
    }

    @Test
    fun decodeSingleLine() {
        testDecode(M1.serializer(), s11, m11)
    }

    val s12 = """
        i = 0 # single
    """.trimIndent()

    @Test
    fun decodeInline() {
        testDecode(M1.serializer(), s12, m11)
    }

    @Serializable
    data class M2(
        @TomlComment("""
            multiline
            comment
        """)
        val i: Int
    )

    val m21 = M2(
        i = 0
    )

    val s21 = """
        # multiline
        # comment
        i = 0
    """.trimIndent()

    @Test
    fun encodeMultiline() {
        testEncode(M2.serializer(), m21, s21)
    }

    @Test
    fun decodeMultiline() {
        testDecode(M2.serializer(), s21, m21)
    }

    @Serializable
    data class M3(
        @TomlComment("test")
        val c: C
    )

    @Serializable
    data class C(
        val f: Float
    )

    val m31 = M3(
        c = C(
            f = 1.1f
        )
    )

    val s31 = """
        # test
        [c]
        f = 1.1
    """.trimIndent()

    @Test
    fun encodeBeforeTable() {
        testEncode(M3.serializer(), m31, s31)
    }

    @Test
    fun decodeBeforeTable() {
        testDecode(M3.serializer(), s31, m31)
    }

    val s32 = """
        [c] # test
        f = 1.1
    """.trimIndent()

    @Test
    fun decodeAfterTableHead() {
        testDecode(M3.serializer(), s32, m31)
    }

    @Serializable
    data class M4(
        @TomlComment("test")
        val cs: List<C>
    )

    val m41 = M4(
        cs = listOf(
            C(
                f = 1.1f
            )
        )
    )

    val s41 = """
        # test
        [[cs]]
        f = 1.1
    """.trimIndent()

    @Test
    fun encodeBeforeArrayOfTable() {
        testEncode(M4.serializer(), m41, s41)
    }

    @Test
    fun decodeBeforeArrayOfTable() {
        testDecode(M4.serializer(), s41, m41)
    }

    val s42 = """
        [[cs]] # test
        f = 1.1
    """.trimIndent()

    @Test
    fun decodeAfterArrayOfTableHead() {
        testDecode(M4.serializer(), s42, m41)
    }

    @Serializable
    data class M5(
        val m1: M1
    )

    val m51 = M5(
        m1 = M1(
            i = -1
        )
    )

    val s51 = """
        [m1]
        # single
        i = -1
    """.trimIndent()

    @Test
    fun encodeInsideTable() {
        testEncode(M5.serializer(), m51, s51)
    }

    @Test
    fun decodeInsideTable() {
        testDecode(M5.serializer(), s51, m51)
    }

    @Serializable
    data class M6(
        val m1s: List<M1>
    )

    val m61 = M6(
        m1s = listOf(
            M1(
                i = 0
            )
        )
    )

    val s61 = """
        [[m1s]]
        # single
        i = 0
    """.trimIndent()

    @Test
    fun encodeInsideArrayOfTable() {
        testEncode(M6.serializer(), m61, s61)
    }

    @Test
    fun decodeInsideArrayOfTable() {
        testDecode(M6.serializer(), s61, m61)
    }

    @Serializable
    data class M7(
        @TomlComment("text")
        val i: Int,
        @TomlComment("another")
        val f: Float
    )

    val m71 = M7(
        i = 0,
        f = 0.1f
    )

    val s71 = """
        # text
        i = 0
        # another
        f = 0.1
    """.trimIndent()

    @Test
    fun encodeLiteralByLiteral() {
        testEncode(M7.serializer(), m71, s71)
    }

    @Test
    fun decodeLiteralByLiteral() {
        testDecode(M7.serializer(), s71, m71)
    }

    @Serializable
    data class M8(
        @TomlComment("text")
        val i: Int,
        @TomlComment("another")
        val c: C
    )

    val m81 = M8(
        i = 0,
        c = C(
            f = 0.1f
        )
    )

    val s81 = """
        # text
        i = 0
        
        # another
        [c]
        f = 0.1
    """.trimIndent()

    @Test
    fun encodeLiteralByTable() {
        testEncode(M8.serializer(), m81, s81)
    }

    @Test
    fun decodeLiteralByTable() {
        testDecode(M8.serializer(), s81, m81)
    }

    @Serializable
    data class M9(
        @TomlComment("""
            multi
            line
        """)
        val i: Int,
        @TomlComment("another")
        val m1: M1
    )

    val m91 = M9(
        i = 0,
        m1 = M1(
            i = 0
        )
    )

    val s91 = """
        # multi
        # line
        i = 0
        
        # another
        [m1]
        # single
        i = 0
    """.trimIndent()

    @Test
    fun encodeComplicated1() {
        testEncode(M9.serializer(), m91, s91)
    }

    @Test
    fun decodeComplicated1() {
        testDecode(M9.serializer(), s91, m91)
    }

    val s92 = """
        # a
        # b
        i = 0 # c
        
        # d
        [m1] # e
        # f
        i = 0 # g
    """.trimIndent()

    @Test
    fun decodeEverywhere1() {
        testDecode(M9.serializer(), s92, m91)
    }

    @Serializable
    data class M10(
        @TomlComment("blocked")
        val m1: M1,
        val i: Int
    )

    val m101 = M10(
        m1 = M1(
            i = 0
        ),
        i = 0
    )

    val s101 = """
        i = 0
        
        # blocked
        [m1]
        # single
        i = 0
    """.trimIndent()

    @Test
    fun encodeSorted() {
        testEncode(M10.serializer(), m101, s101)
    }

    @Test
    fun decodeSorted() {
        testDecode(M10.serializer(), s101, m101)
    }

    val s102 = """
        # a
        # b
        m1.i = 0 # c
        # d
        i = 0 # e
    """.trimIndent()

    @Test
    fun decodeEverywhere2() {
        testDecode(M10.serializer(), s102, m101)
    }

    @Serializable
    data class M11(
        @TomlComment("""
            what are
            you doing
        """)
        val i: Int,
        @TomlComment("""
            this is
            my life
        """)
        val m1s: List<M1>,
        @TomlComment("""
            oh
            my
            god
        """)
        val m1: M1
    )

    val m111 = M11(
        i = -1,
        m1s = listOf(
            M1(
                i = 0
            )
        ),
        m1 = M1(
            i = 1
        )
    )

    val s111 = """
        # what are
        # you doing
        i = -1

        # oh
        # my
        # god
        [m1]
        # single
        i = 1

        # this is
        # my life
        [[m1s]]
        # single
        i = 0
    """.trimIndent()

    @Test
    fun encodeComplicated2() {
        testEncode(M11.serializer(), m111, s111)
    }

    @Test
    fun decodeComplicated2() {
        testDecode(M11.serializer(), s111, m111)
    }

    val s112 = """
        # a
        i = -1
        # b
        [[m1s]] # c
        # d
        i = 0 # e
        # f
        [m1] # g
        # h
        i = 1 # i
    """.trimIndent()

    @Test
    fun decodeEverywhere3() {
        testDecode(M11.serializer(), s112, m111)
    }

    @Serializable
    data class M12(
        @TomlComment("""
            原神
            启动！
        """)
        val b: Boolean
    )

    val m121 = M12(
        b = true
    )

    val s121 = """
        # 原神
        # 启动！
        b = true
    """.trimIndent()

    @Test
    fun encodeUnicode() {
        testEncode(M12.serializer(), m121, s121)
    }

    @Test
    fun decodeUnicode() {
        testDecode(M12.serializer(), s121, m121)
    }

    @Serializable
    data class M13(
        @TomlComment("")
        val s: String
    )

    val m131 = M13(
        s = ""
    )

    val s131 = """
        # 
        s = ""
    """.trimIndent()

    @Test
    fun encodeEmpty() {
        testEncode(M13.serializer(), m131, s131)
    }

    @Test
    fun decodeEmpty() {
        testDecode(M13.serializer(), s131, m131)
    }
}
