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
import kotlin.math.pow

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

internal const val DecimalConstraints: String = "0123456789"

internal const val HexadecimalConstraints: String = "0123456789" + "abcdef" + "ABCDEF"

internal const val DecimalOrSignConstraints: String = "0123456789" + "-+"

internal const val BareKeyConstraints: String =
    "abcdefghijklmnopqrstuvwxyz" + "-_" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789"

internal const val DefiniteDateTimeConstraints: String = "Tt:Zz"

internal const val DefiniteNumberConstraints: String = "." + "acdef" + "ABCDEF" + "_"

internal val BareKeyRegex: Regex = Regex("[A-Za-z0-9_-]+")

internal val AsciiMapping: List<String> = buildList(128) {
    for (i in 0x00..0x1f) {
        add(i, "\\u00" + i.toString(16).padStart(2, '0'))
    }
    for (i in 0x20..0x7e) {
        add(i, i.toChar().toString())
    }
    add(0x7f, "\\u007f")
    set('\b'.code, "\\b")
    set('\t'.code, "\\t")
    set('\n'.code, "\\n")
    set(12, "\\f")
    set('\r'.code, "\\r")
    set('\"'.code, "\\\"")
    set('\\'.code, "\\\\")
}

internal inline val String.singleQuoted: String
    get() = "\'$this\'"

internal inline val String.doubleQuoted: String
    get() = "\"$this\""

internal fun String.doubleQuotedIfNotPure(): String {
    return if (BareKeyRegex matches this) this else doubleQuoted
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

internal fun String.escape(multiline: Boolean = false): String {
    val builder = StringBuilder()
    for (c in this) {
        builder.append(c.escape(multiline))
    }
    return builder.toString()
}

internal fun String.unescape(): String {
    if (isBlank()) {
        return this
    }
    val builder = StringBuilder()
    val lastIndex = lastIndex
    var i = 0
    while (i <= lastIndex) {
        val current = get(i)
        if (current != '\\') {
            builder.append(current)
            i++
            continue
        }
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
            'u' -> {
                // \u0000.
                require(lastIndex >= i + 5) { "Unexpected end in $this" }
                val char = substring(i + 2, i + 6).toInt(16).toChar()
                builder.append(char)
                i += 5
            }
            'U' -> {
                // \U00000000.
                require(lastIndex >= i + 9) { "Unexpected end in $this" }
                val char = substring(i + 2, i + 10).toInt(16).toChar()
                builder.append(char)
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
    if (isDouble) {
        var factor = if (isPositive) 1.0 else -1.0
        if (isExponent) {
            val strings = content.split('e', ignoreCase = true)
            factor *= 10.0.pow(strings[1].toInt())
            return TomlLiteral(strings[0].toDouble() * factor)
        }
        return TomlLiteral(content.toDouble() * factor)
    }
    var factor = if (isPositive) 1L else -1L
    if (isExponent) {
        val strings = content.split('e', ignoreCase = true)
        factor *= 10.0.pow(strings[1].toInt()).toLong()
        return TomlLiteral(strings[0].toLong(radix) * factor)
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
    return TomlLiteral(long * factor)
}
