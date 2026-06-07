package dev.eav.tomlkt

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for issues fixed against the toml-test compliance suite.
 * Each test covers one fix; comments name the relevant toml-test case(s).
 */
class ComplianceFixesTest {
    private fun literal(input: String): TomlLiteral =
        Toml.parseToTomlTable(input).getLiteral("x")

    // valid/float/exponent, valid/float/zero, valid/spec/float-0:
    // a number with an exponent is a float even without a fractional part.
    @Test
    fun exponentWithoutFractionIsFloat() {
        assertEquals(TomlLiteral.Type.Float, literal("x = 3e2").type)
        assertEquals(TomlLiteral.Type.Float, literal("x = 0e0").type)
        assertEquals(300.0, literal("x = 3e2").toDouble())
    }

    // valid/comment/tricky: 1.11e1 must decode to 11.1, not 11.100000000000001.
    @Test
    fun exponentFloatKeepsPrecision() {
        assertEquals(11.1, literal("x = 1.11e1").toDouble())
    }

    // invalid/integer/{negative,positive}-{bin,hex,oct}: no sign on radix integers.
    @Test
    fun signedRadixIntegersRejected() {
        testInvalid("x = -0xff")
        testInvalid("x = +0xff")
        testInvalid("x = -0o17")
        testInvalid("x = +0b1")
    }

    // invalid/integer/leading-zero*, invalid/float/leading-zero*.
    @Test
    fun leadingZerosRejected() {
        testInvalid("x = 01")
        testInvalid("x = 00")
        testInvalid("x = 0_0")
        testInvalid("x = 03.14")
        testInvalid("x = -01")
        testInvalid("x = +0_1")
    }

    // invalid/integer/us-after-bin: an underscore cannot follow a radix prefix.
    @Test
    fun underscoreAfterRadixPrefixRejected() {
        testInvalid("x = 0b_1")
    }

    // invalid/control/*: raw control characters are forbidden in strings and comments.
    @Test
    fun controlCharactersRejected() {
        val nul = Char(0x00)
        val del = Char(0x7F)
        val cr = Char(0x0D)
        testInvalid("x = \"a${nul}b\"") // NUL in a basic string
        testInvalid("x = \"a${Char(0x08)}b\"") // backspace in a basic string
        testInvalid("x = \"a${del}b\"") // DEL in a basic string
        testInvalid("x = 'a${nul}b'") // NUL in a literal string
        testInvalid("# a${nul}b") // NUL in a comment
        testInvalid("x = \"a${cr}b\"") // a bare carriage return
    }

    // valid/string/escape-esc, valid/string/hex-escape: TOML 1.1 escapes.
    @Test
    fun toml11Escapes() {
        assertEquals(Char(0x1B).toString(), literal("x = \"\\e\"").content) // \e -> ESC
        assertEquals("A", literal("x = \"\\x41\"").content) // \x41 -> 'A'
    }

    // valid/string/quoted-unicode, valid/key/quoted-unicode: astral \U escapes
    // are emitted as a UTF-16 surrogate pair, not truncated.
    @Test
    fun astralUnicodeEscape() {
        // U+1F600 GRINNING FACE -> surrogate pair D83D DE00.
        assertEquals("😀", literal("x = \"\\U0001F600\"").content)
    }

    // invalid/string/*out-of-range-unicode-escape*, invalid/string/bad-uni-esc-6.
    @Test
    fun outOfRangeUnicodeEscapeRejected() {
        testInvalid("x = \"\\UFFFFFFFF\"") // beyond U+10FFFF
        testInvalid("x = \"\\uD800\"") // a lone surrogate
    }

    // valid/string/multiline-quotes, valid/spec/string-4, valid/spec/string-7:
    // quotes may sit right against the closing delimiter.
    @Test
    fun quotesAdjacentToMultilineDelimiter() {
        assertEquals("\"a\"", literal("x = \"\"\"\"a\"\"\"\"").content)
        assertEquals("'a'", literal("x = ''''a''''").content)
    }

    // valid/string/multiline-escaped-crlf: a line-ending backslash before a CRLF.
    @Test
    fun lineEndingBackslashBeforeCrlf() {
        val crlf = "${Char(0x0D)}${Char(0x0A)}"
        assertEquals("ab", literal("x = \"\"\"a\\${crlf}  b\"\"\"").content)
    }

    // valid/comment/everywhere: a local date followed by a comment.
    @Test
    fun localDateBeforeComment() {
        val date = literal("x = 1979-05-27 # comment")
        assertEquals(TomlLiteral.Type.LocalDate, date.type)
        assertEquals("1979-05-27", date.content)
    }

    // valid/inline-table/newline: TOML 1.1 newlines and trailing commas.
    @Test
    fun inlineTableNewlinesAndTrailingComma() {
        assertEquals(1L, Toml.parseToTomlTable("x = { a = 1, }").getTable("x").getInteger("a"))

        val nl = Char(0x0A)
        val multiline = Toml.parseToTomlTable("x = {${nl}  a = 1,${nl}  b = 2,${nl}}").getTable("x")
        assertEquals(1L, multiline.getInteger("a"))
        assertEquals(2L, multiline.getInteger("b"))
    }

    // valid/inline-table/newline-comment: comments may appear wherever a newline
    // may inside an inline table.
    @Test
    fun inlineTableComments() {
        val nl = Char(0x0A)
        val table = Toml.parseToTomlTable(
            "x = {#c$nl  a = 1,#c$nl  #c$nl  b = 2,$nl}"
        ).getTable("x")
        assertEquals(1L, table.getInteger("a"))
        assertEquals(2L, table.getInteger("b"))
    }

    // invalid/float/trailing-exp-{plus,minus}: an exponent sign needs a digit.
    @Test
    fun exponentSignWithoutDigitRejected() {
        testInvalid("x = 0.0e+")
        testInvalid("x = 0.0e-")
        testInvalid("x = 1e+")
    }

    // invalid/datetime/second-trailing-dot{,z}, invalid/local-time/trailing-dot:
    // a fractional-second dot needs at least one digit.
    @Test
    fun trailingFractionalDotRejected() {
        testInvalid("x = 12:13:14.")
        testInvalid("x = 1997-09-09T09:09:09.")
        testInvalid("x = 2016-09-09T09:09:09.Z")
    }

    // invalid/datetime/offset-{plus,minus}-no-minute: a numeric offset must
    // carry minutes, i.e. (+|-)HH:MM rather than a bare hour.
    @Test
    fun numericOffsetWithoutMinutesRejected() {
        testInvalid("x = 1997-09-09T09:09:09.09+09")
        testInvalid("x = 1997-09-09T09:09:09.09-09")
    }

    // The valid offset forms keep working.
    @Test
    fun numericOffsetWithMinutesAccepted() {
        assertEquals(
            TomlLiteral.Type.OffsetDateTime,
            literal("x = 1997-09-09T09:09:09.09+09:30").type
        )
        assertEquals(
            TomlLiteral.Type.OffsetDateTime,
            literal("x = 1997-09-09T09:09:09Z").type
        )
    }
}
