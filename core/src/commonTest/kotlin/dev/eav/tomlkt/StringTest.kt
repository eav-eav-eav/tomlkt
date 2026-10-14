package dev.eav.tomlkt

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class StringTest {
    @Serializable
    data class M1(
        val s: String
    )

    val m11 = M1(
        s = "beam"
    )

    val s11 = """
        s = "beam"
    """.trimIndent()

    @Test
    fun encodeRegularly() {
        testEncode(M1.serializer(), m11, s11)
    }

    @Test
    fun decodeRegularly() {
        testDecode(M1.serializer(), s11, m11)
    }

    val m12 = M1(
        s = "'a'"
    )

    val s12 = """
        s = "'a'"
    """.trimIndent()

    @Test
    fun encodeSingleQuote() {
        testEncode(M1.serializer(), m12, s12)
    }

    @Test
    fun decodeSingleQuote() {
        testDecode(M1.serializer(), s12, m12)
    }

    val m13 = M1(
        s = "\b \t \n " + 12.toChar() + " \r \" \\"
    )

    val s13 = """
        s = "\b \t \n \f \r \" \\"
    """.trimIndent()
    
    @Test
    fun encodePopularEscapedChars() {
        testEncode(M1.serializer(), m13, s13)
    }

    @Test
    fun decodePopularEscapedChars() {
        testDecode(M1.serializer(), s13, m13)
    }

    @Test
    fun encodeControlCharsAsHexEscapes() {
        val value = M1(s = "\u0000\u000b\u0010\u001f\u007f")
        testEncode(M1.serializer(), value, "s = \"\\u0000\\u000b\\u0010\\u001f\\u007f\"")
    }

    @Test
    fun controlCharsRoundTrip() {
        val value = M1(s = (0x00..0x1f).map { it.toChar() }.joinToString("") + "\u007f")
        val encoded = Toml.encodeToString(M1.serializer(), value)
        assertEquals(value, Toml.decodeFromString(M1.serializer(), encoded))
    }

    val m14 = M1(
        s = "\u539f\u795e\uff0c\u542f\u52a8\uff01"
    )

    val s14 = """
        s = "\u539f\u795e\uff0c\u542f\u52a8\uff01"
    """.trimIndent()

    @Test
    fun decodeUnicode() {
        testDecode(M1.serializer(), s14, m14)
    }

    @Serializable
    data class M2(
        @TomlMultilineString
        val s: String
    )

    val m21 = M2(
        s = """
            R
            G
            B
        """.trimIndent()
    )

    val s21 = """
        s = ""${'"'}
        R
        G
        B""${'"'}
    """.trimIndent()

    @Test
    fun encodeMultiline() {
        testEncode(M2.serializer(), m21, s21)
    }

    @Test
    fun decodeMultiline() {
        testDecode(M2.serializer(), s21, m21)
    }

    val s22 = """
        s = ""${'"'}
        R
        \      
        
                    G
        \                 
                                  B""${'"'}
    """.trimIndent()
    
    @Test
    fun decodeTrimmer() {
        testDecode(M2.serializer(), s22, m21)
    }

    val m22 = M2(
        s = """
            R \
            G
            B
        """.trimIndent()
    )

    val s23 = """
        s = ""${'"'}
        R \\
        G
        B""${'"'}
    """.trimIndent()

    @Test
    fun encodeMultilineWithBackslash() {
        testEncode(M2.serializer(), m22, s23)
    }

    @Test
    fun decodeMultilineWithBackslash() {
        testDecode(M2.serializer(), s23, m22)
    }

    val m23 = M2(
        s = """Here are two quotation marks: ""."""
    )
    
    val s24 = """
        s = ""${'"'}Here are two quotation marks: "".""${'"'}
    """.trimIndent()
    
    @Test
    fun decodeManyDoubleQuote1() {
        testDecode(M2.serializer(), s24, m23)
    }

    val m24 = M2(
        s = """Here are nine quotation marks: ""${'"'}""${'"'}""${'"'}."""
    )

    val s25 = """
        s = ""${'"'}Here are nine quotation marks: ""\""${'"'}\""${'"'}\".""${'"'}
    """.trimIndent()

    @Test
    fun decodeManyDoubleQuote2() {
        testDecode(M2.serializer(), s25, m24)
    }

    val m25 = M2(
        s = """"This," she said, "is just a pointless statement.""""
    )

    val s26 = """
        s = ""${'"'}"This," she said, "is just a pointless statement.\""${'"'}"
    """.trimIndent()

    @Test
    fun decodeManyDoubleQuote3() {
        testDecode(M2.serializer(), s26, m25)
    }

    @Serializable
    data class M3(
        @TomlLiteralString
        val s: String
    )

    val m31 = M3(
        s = """<\i\c*\s*>"""
    )

    val s31 = """
        s = '<\i\c*\s*>'
    """.trimIndent()

    @Test
    fun encodeLiteral() {
        testEncode(M3.serializer(), m31, s31)
    }

    @Test
    fun decodeLiteral() {
        testDecode(M3.serializer(), s31, m31)
    }

    @Serializable
    data class M4(
        @TomlMultilineString
        @TomlLiteralString
        val s: String
    )

    val m41 = M4(
        s = """
            DON'T!
            1
              2
            
        """.trimIndent()
    )

    val s41 = """
        s = '''
        DON'T!
        1
          2
        '''
    """.trimIndent()

    @Test
    fun encodeMultilineLiteral() {
        testEncode(M4.serializer(), m41, s41)
    }

    @Test
    fun decodeMultilineLiteral() {
        testDecode(M4.serializer(), s41, m41)
    }
}
