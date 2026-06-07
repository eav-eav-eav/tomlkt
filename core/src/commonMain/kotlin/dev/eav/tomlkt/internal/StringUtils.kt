/*
    Copyright 2026 Loney Chou

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */

package dev.eav.tomlkt.internal

import dev.eav.tomlkt.TomlInteger.Base
import dev.eav.tomlkt.TomlInteger.Base.Dec
import dev.eav.tomlkt.TomlLiteral

internal typealias Path = List<String>

internal const val Comment = '#'

internal const val KeySeparator = '.'

internal const val KeyValueSeparator = '='

internal const val ElementSeparator = ','

internal const val StartTableHead = '['

internal const val EndTableHead = ']'

internal const val StartArray = '['

internal const val EndArray = ']'

internal const val StartInlineTable = '{'

internal const val EndInlineTable = '}'

internal const val DefiniteDateTimeConstraints: String = "Tt:Zz"

internal const val DefiniteNumberConstraints: String = "." + "acdef" + "ABCDEF" + "_"

internal val BareKeyRegex: Regex = Regex("[A-Za-z0-9_-]+")

// Character-class checks for the parser hot path. These compile to range
// comparisons (cheap bounds checks, no allocation) instead of scanning a
// constraint String with `String.contains` for every character.

internal fun Char.isDecimalDigit(): Boolean = this in '0'..'9'

internal fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

internal fun Char.isDecimalOrSign(): Boolean =
    this in '0'..'9' || this == '+' || this == '-'

// Usable as `char in BareKeyConstraints` in a `when`, like the former String,
// but without the per-character linear scan.
internal object BareKeyConstraints {
    operator fun contains(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '-' || char == '_'
}

internal val AsciiMapping: List<String> = buildList(128) {
    for (i in 0x00..0x1f) {
        // Zero-padded four-digit hex. The old code interpolated the Int in
        // decimal, so ESC (0x1b) wrongly became the escape backslash-u-0027.
        add(i, "\\u" + i.toString(16).padStart(4, '0'))
    }
    for (i in 0x20..0x7f) {
        add(i, i.toChar().toString())
    }
    set('\b'.code, "\\b")
    set('\t'.code, "\\t")
    set('\n'.code, "\\n")
    set(12, "\\f")
    set('\r'.code, "\\r")
    set('\"'.code, "\\\"")
    set('\\'.code, "\\\\")
    // DEL is a control character that a basic string must escape.
    set(0x7f, "\\u007f")
}

internal inline val String.singleQuoted: String
    get() = "\'$this\'"

internal inline val String.doubleQuoted: String
    get() = "\"$this\""

internal fun String.doubleQuotedIfNotPure(): String {
    return if (BareKeyRegex matches this) this else doubleQuoted
}

// A key that can be written bare: non-empty and every character is a bare-key
// character. Lets the writer skip the escape + regex that quoting requires for
// the overwhelmingly common case of an already-bare key (see writeKey). A bare
// key never contains a character that escaping would change, so this is exactly
// the set the former `escape().doubleQuotedIfNotPure()` left unquoted.
internal fun String.isBareKey(): Boolean {
    if (isEmpty()) {
        return false
    }
    for (c in this) {
        if (c !in BareKeyConstraints) {
            return false
        }
    }
    return true
}

internal fun Char.escape(multiline: Boolean = false): String {
    return when {
        code >= 128 -> toString()
        !multiline -> AsciiMapping[code]
        this == '\\' -> "\\\\"
        this == '\t' -> "\t"
        this == '\n' -> "\n"
        this == '\r' -> "\r"
        else -> AsciiMapping[code]
    }
}

// True when [Char.escape] would render this character as something other than
// itself, i.e. the character needs an escape sequence. Mirrors the cases in
// [Char.escape] exactly: a non-multiline string escapes every control character
// (code < 0x20) plus the quote and backslash; a multiline string keeps tab,
// line feed and carriage return literal, so only the remaining control
// characters (plus quote and backslash) are escaped. DEL (0x7f) is a control
// character too and is escaped in both modes.
private fun Char.needsEscape(multiline: Boolean): Boolean {
    return if (!multiline) {
        code < 0x20 || code == 0x7f || this == '\"' || this == '\\'
    } else {
        this == '\\' || this == '\"' || code == 0x7f || (code < 0x20 && this != '\t' && this != '\n' && this != '\r')
    }
}

internal fun String.escape(multiline: Boolean = false): String {
    // Single pass, one needsEscape check per character. The builder is created
    // only once the first character that needs escaping is seen; until then the
    // string is its own escaped form, so a string that needs no escaping at all
    // (the common case for keys and plain values) is returned without
    // allocating anything. Plain runs between escapes are bulk-copied rather
    // than appended one String per char.
    val length = length
    var builder: StringBuilder? = null
    var runStart = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c.needsEscape(multiline)) {
            val target = builder ?: StringBuilder(length + 16).also { builder = it }
            if (i > runStart) {
                target.appendRange(this, runStart, i)
            }
            target.append(c.escape(multiline))
            runStart = i + 1
        }
        i++
    }
    val target = builder ?: return this
    if (runStart < length) {
        target.appendRange(this, runStart, length)
    }
    return target.toString()
}

// Only called once an escape is known to be present (see parseStringValue), so
// the string always contains at least one backslash.
internal fun String.unescape(): String {
    val builder = StringBuilder()
    val lastIndex = lastIndex
    var i = 0
    while (i <= lastIndex) {
        // Bulk-copy the run of plain characters up to the next backslash
        // (indexOf is an intrinsic) rather than appending one char at a time.
        val backslash = indexOf('\\', i)
        if (backslash < 0) {
            builder.appendRange(this, i, length)
            break
        }
        builder.appendRange(this, i, backslash)
        i = backslash
        require(i != lastIndex) { "Unexpected end in $this" }
        when (val next = get(i + 1)) {
            'n' -> {
                builder.append('\n')
                i++
            }
            '\"' -> {
                builder.append('\"')
                i++
            }
            '\\' -> {
                builder.append('\\')
                i++
            }
            'e' -> {
                // \e -> ESC (U+001B), a TOML 1.1 addition.
                builder.append(27.toChar())
                i++
            }
            'x' -> {
                // \xHH, a TOML 1.1 addition.
                require(lastIndex >= i + 3) { "Unexpected end in $this" }
                builder.appendScalar(substring(i + 2, i + 4).toInt(16))
                i += 3
            }
            'u' -> {
                // \u0000.
                require(lastIndex >= i + 5) { "Unexpected end in $this" }
                builder.appendScalar(substring(i + 2, i + 6).toInt(16))
                i += 5
            }
            'U' -> {
                // \U00000000.
                require(lastIndex >= i + 9) { "Unexpected end in $this" }
                builder.appendScalar(substring(i + 2, i + 10).toInt(16))
                i += 9
            }
            't' -> {
                builder.append('\t')
                i++
            }
            'r' -> {
                builder.append('\r')
                i++
            }
            'b' -> {
                builder.append('\b')
                i++
            }
            'f' -> {
                builder.append(12.toChar())
                i++
            }
            else -> {
                error("Unknown escape $next")
            }
        }
        i++
    }
    return builder.toString()
}

// Appends a Unicode scalar value (from a \u/\U escape) as UTF-16, encoding
// astral code points as a surrogate pair. Rejects values outside the Unicode
// scalar range (> U+10FFFF or a lone surrogate).
private fun StringBuilder.appendScalar(codePoint: Int) {
    require(codePoint in 0x0..0x10FFFF && codePoint !in 0xD800..0xDFFF) {
        "Invalid Unicode scalar value: U+${codePoint.toString(16)}"
    }
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xD800 + (offset shr 10)).toChar())
        append((0xDC00 + (offset and 0x3FF)).toChar())
    }
}

// Control characters TOML forbids inside strings and comments. Tab is allowed;
// newline and carriage return are handled separately by the parser, so they
// never reach this check.
internal fun Char.isForbiddenControlChar(): Boolean = code < 0x20 && this != '\t' || code == 0x7F

internal fun Float.toStringModified(): String {
    return when {
        isNaN() -> "nan"
        isInfinite() -> if (this > 0.0f) "inf" else "-inf"
        else -> toString()
    }
}

internal fun Double.toStringModified(): String {
    return when {
        isNaN() -> "nan"
        isInfinite() -> if (this > 0.0) "inf" else "-inf"
        else -> toString()
    }
}

internal fun processIntegerString(
    raw: String,
    base: Base,
    group: Int,
    uppercase: Boolean
): String {
    val isNegative = raw[0] == '-'
    val digits = if (!isNegative) {
        raw
    } else {
        raw.substring(1)
    }
    val upper = if (base <= Dec || !uppercase) {
        digits
    } else {
        digits.uppercase()
    }
    val grouped = if (group == 0) {
        upper
    } else {
        upper.reversed()
            .chunked(group, CharSequence::reversed)
            .asReversed()
            .joinToString(separator = "_")
    }
    val result = if (!isNegative) {
        base.prefix + grouped
    } else {
        "-" + base.prefix + grouped
    }
    return result
}

internal fun createNumberTomlLiteral(
    content: String,
    isPositive: Boolean,
    radix: Int,
    isDouble: Boolean,
    isExponent: Boolean
): TomlLiteral {
    // A number carrying an exponent is always a float, even without a
    // fractional part: `3e2` is the float 300.0, not the integer 300.
    if (isDouble || isExponent) {
        // The parser already validated the float's structure, so keep its text
        // as the content rather than parsing to a Double and re-stringifying.
        return TomlLiteral(content.signed(isPositive), TomlLiteral.Type.Float)
    }
    val long = content.toLongOrNull(radix)
    if (long == null) {
        if (isPositive) {
            // This is a ULong.
            return TomlLiteral(content.toULong(radix))
        }
        require(content == "9223372036854775808") { "ULong cannot be negative" }
        // This is Long.MIN_VALUE.
        return TomlLiteral(Long.MIN_VALUE)
    }
    if (radix == 10) {
        // A decimal integer's text is already canonical; reuse it directly.
        // A bare zero is never signed, so -0 normalises to "0".
        val text = if (isPositive || long == 0L) content else "-$content"
        return TomlLiteral(text, TomlLiteral.Type.Integer)
    }
    val factor = if (isPositive) 1L else -1L
    return TomlLiteral(long * factor)
}

private fun String.signed(isPositive: Boolean): String = if (isPositive) this else "-$this"
