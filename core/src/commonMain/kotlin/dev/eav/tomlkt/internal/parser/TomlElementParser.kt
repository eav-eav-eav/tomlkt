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

package dev.eav.tomlkt.internal.parser

import dev.eav.tomlkt.NativeLocalDate
import dev.eav.tomlkt.NativeLocalDateTime
import dev.eav.tomlkt.NativeLocalTime
import dev.eav.tomlkt.NativeOffsetDateTime
import dev.eav.tomlkt.TomlArray
import dev.eav.tomlkt.TomlComment
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlLiteral.Type
import dev.eav.tomlkt.TomlLiteralString
import dev.eav.tomlkt.TomlMultilineString
import dev.eav.tomlkt.TomlNull
import dev.eav.tomlkt.TomlReader
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.internal.BareKeyConstraints
import dev.eav.tomlkt.internal.Comment
import dev.eav.tomlkt.internal.DefiniteDateTimeConstraints
import dev.eav.tomlkt.internal.DefiniteNumberConstraints
import dev.eav.tomlkt.internal.ElementSeparator
import dev.eav.tomlkt.internal.EndArray
import dev.eav.tomlkt.internal.EndInlineTable
import dev.eav.tomlkt.internal.EndTableHead
import dev.eav.tomlkt.internal.KeySeparator
import dev.eav.tomlkt.internal.KeyValueSeparator
import dev.eav.tomlkt.internal.Path
import dev.eav.tomlkt.internal.StartArray
import dev.eav.tomlkt.internal.StartInlineTable
import dev.eav.tomlkt.internal.StartTableHead
import dev.eav.tomlkt.internal.createNumberTomlLiteral
import dev.eav.tomlkt.internal.isDecimalDigit
import dev.eav.tomlkt.internal.isDecimalOrSign
import dev.eav.tomlkt.internal.isForbiddenControlChar
import dev.eav.tomlkt.internal.isHexDigit
import dev.eav.tomlkt.internal.throwConflictEntry
import dev.eav.tomlkt.internal.throwIncomplete
import dev.eav.tomlkt.internal.throwUnexpectedToken
import dev.eav.tomlkt.internal.unescape
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class TomlElementParser private constructor(
    private val reader: TomlReader?,
    // When the whole input is in memory, the parser reads it by index. This
    // also unlocks bulk copying of plain string content (see parseStringValue).
    private val source: CharSequence?
) {
    constructor(reader: TomlReader) : this(reader, source = null)

    constructor(source: CharSequence) : this(reader = null, source = source)

    private val sourceLength: Int = source?.length ?: 0

    private var sourceIndex: Int = 0

    private var currentChar: Char = 0.toChar()

    private var previousChar: Char = 0.toChar()

    private var currentLineNumber: Int = 1

    private var isEof: Boolean = false

    // Standalone comment lines collected since the last entry, attached as a
    // TomlComment to the next key or table so they survive a round trip.
    private val pendingComments = StringBuilder()

    // Style of the most recently parsed scalar string value, so the entry can
    // be annotated to re-emit it the same way.
    private var lastStringMultiline = false

    private var lastStringLiteral = false

    // Inline comment (after the value on the same line) of the entry being read.
    private var lastInlineComment: String? = null

    // region Read

    private fun proceed() {
        val source = source
        if (source != null) {
            if (sourceIndex < sourceLength) {
                previousChar = currentChar
                currentChar = source[sourceIndex++]
            } else {
                isEof = true
            }
            return
        }
        if (isEof) {
            return
        }
        val code = reader!!.read()
        if (code != -1) {
            previousChar = currentChar
            currentChar = code.toChar()
        } else {
            isEof = true
        }
    }

    private fun getCurrent(): Char {
        return currentChar
    }

    private fun getPrevious(): Char {
        return previousChar
    }

    // endregion

    // region Check

    private fun throwIncomplete(): Nothing {
        throwIncomplete(currentLineNumber)
    }

    private inline fun throwIncompleteIf(predicate: () -> Boolean) {
        contract { callsInPlace(predicate, InvocationKind.EXACTLY_ONCE) }

        if (predicate()) {
            throwIncomplete()
        }
    }

    private fun throwUnexpectedToken(token: Char): Nothing {
        throwUnexpectedToken(token, currentLineNumber)
    }

    private inline fun throwUnexpectedTokenIf(token: Char, predicate: (Char) -> Boolean) {
        contract { callsInPlace(predicate, InvocationKind.EXACTLY_ONCE) }

        if (predicate(token)) {
            throwUnexpectedToken(token)
        }
    }

    private fun throwConflictEntry(path: Path): Nothing {
        throwConflictEntry(path, currentLineNumber)
    }

    private fun expectNext(expectedToken: Char) {
        proceed()
        throwIncompleteIf { isEof }
        throwUnexpectedTokenIf(getCurrent()) { it != expectedToken }
    }

    private fun expectNext(expectedTokens: String) {
        for (expectedToken in expectedTokens) {
            expectNext(expectedToken)
        }
    }

    // After a table head only trailing whitespace and an optional inline comment
    // may follow before the line ends; anything else (e.g. another key on the
    // same line) is invalid. The inline comment, if any, is recorded so that
    // [takeEntryAnnotations] can attach it to the table head.
    private fun expectLineEnd() {
        lastInlineComment = null
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    currentLineNumber++
                    proceed()
                    return
                }
                '\r' -> {
                    proceed()
                    throwUnexpectedTokenIf(current) { isEof || getCurrent() != '\n' }
                    currentLineNumber++
                    proceed()
                    return
                }
                Comment -> {
                    lastInlineComment = parseComment()
                }
                else -> {
                    throwUnexpectedToken(current)
                }
            }
        }
    }

    // endregion

    fun parse(): TomlTable {
        val tree = KeyNode("", isLast = false)
        val arrayOfTableIndices = mutableMapOf<Path, Int>()
        var currentTablePath: Path? = null
        proceed()
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    currentLineNumber++
                    proceed()
                }
                '\r' -> {
                    proceed()
                    throwUnexpectedTokenIf(current) { isEof || getCurrent() != '\n' }
                }
                in BareKeyConstraints, '\"', '\'' -> {
                    val localPath = parsePath()
                    throwUnexpectedTokenIf(getCurrent()) { it != KeyValueSeparator }
                    proceed()
                    val key = localPath.last()
                    val value = parseValue(isInsideStructure = false)
                    val node = ValueNode(key, value)
                    // The enclosing table's path is the header prefix; localPath
                    // is the dotted-key suffix that may not extend header-defined
                    // tables.
                    val suffixStart = currentTablePath?.size ?: 0
                    val path = if (currentTablePath != null) currentTablePath + localPath else localPath
                    if (tree.addByPath(path, node, arrayOfTableIndices, takeEntryAnnotations(value), suffixStart).not()) {
                        throwConflictEntry(path)
                    }
                }
                Comment -> {
                    if (pendingComments.isNotEmpty()) {
                        pendingComments.append('\n')
                    }
                    pendingComments.append(parseComment())
                }
                StartTableHead -> {
                    proceed()
                    throwIncompleteIf { isEof }
                    val isArrayOfTable = getCurrent() == StartTableHead
                    if (isArrayOfTable) {
                        proceed()
                    }
                    val path = parseTableHead(isArrayOfTable)
                    // Only whitespace/comment may follow on the head's line.
                    expectLineEnd()
                    // Comments collected above the table head attach to it.
                    val headAnnotations = takeEntryAnnotations(null)
                    if (!isArrayOfTable) {
                        val key = path.last()
                        val node = KeyNode(key, isLast = true)
                        if (tree.addByPath(path, node, arrayOfTableIndices, headAnnotations).not()) {
                            throwConflictEntry(path)
                        }
                    } else {
                        val iterator = arrayOfTableIndices.keys.iterator()
                        for (key in iterator) {
                            if (key != path && key.containsAll(path)) {
                                iterator.remove()
                            }
                        }
                        val currentIndex = arrayOfTableIndices[path]
                        if (currentIndex == null) {
                            arrayOfTableIndices[path] = 0
                            val key = path.last()
                            val node = ArrayNode(key)
                            if (tree.addByPath(path, node, arrayOfTableIndices, headAnnotations).not()) {
                                throwConflictEntry(path)
                            }
                        } else {
                            arrayOfTableIndices[path] = currentIndex + 1
                        }
                        // A virtual node to act like the root of an array element.
                        val node = KeyNode("", isLast = false)
                        tree.getByPath<ArrayNode>(path, arrayOfTableIndices).add(node)
                    }
                    currentTablePath = path
                }
                else -> {
                    throwUnexpectedToken(current)
                }
            }
        }
        return TomlTable(tree)
    }

    /**
     * Start right on the actual token, end right after the last ']'.
     */
    private fun parseTableHead(isArrayOfTable: Boolean): Path {
        var path: Path? = null
        var justEnded = false
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    throwIncomplete()
                }
                EndTableHead -> {
                    if (isArrayOfTable) {
                        expectNext(EndTableHead)
                    }
                    justEnded = true
                    proceed()
                    break
                }
                else -> {
                    throwUnexpectedTokenIf(current) { path != null }
                    path = parsePath()
                }
            }
        }
        throwIncompleteIf { path == null || !justEnded }
        return path!!
    }

    /**
     * Start right on the actual token, end right on '=' or ']'.
     */
    private fun parsePath(): Path {
        val path = mutableListOf<String>()
        var justEnded = false
        var expectKey = true
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    throwIncomplete()
                }
                in BareKeyConstraints -> {
                    throwUnexpectedTokenIf(current) { !expectKey }
                    path.add(parseBareKey())
                    expectKey = false
                }
                '\"' -> {
                    throwUnexpectedTokenIf(current) { !expectKey }
                    path.add(parseStringKey())
                    expectKey = false
                }
                '\'' -> {
                    throwUnexpectedTokenIf(current) { !expectKey }
                    path.add(parseLiteralStringKey())
                    expectKey = false
                }
                KeySeparator -> {
                    throwUnexpectedTokenIf(current) { expectKey }
                    expectKey = true
                    proceed()
                }
                KeyValueSeparator, EndArray -> {
                    throwUnexpectedTokenIf(current) { expectKey }
                    justEnded = true
                    break
                }
                else -> {
                    throwUnexpectedToken(current)
                }
            }
        }
        throwIncompleteIf { !justEnded }
        return path
    }

    /**
     * Start right on the actual token, end right on ' ' or '\t' or '.' or '='
     * or ']'.
     */
    private fun parseBareKey(): String {
        val src = source
        if (src != null) {
            // Bulk-scan the run of bare-key characters and slice it out in one
            // allocation, with no StringBuilder or per-character dispatch. The
            // caller (parsePath) validates whatever character stops the run, so
            // the same tokens are rejected as the char-by-char path below.
            val begin = sourceIndex - 1
            var end = begin
            while (end < sourceLength && src[end] in BareKeyConstraints) {
                end++
            }
            val key = src.subSequence(begin, end).toString()
            if (end < sourceLength) {
                previousChar = src[end - 1]
                currentChar = src[end]
                sourceIndex = end + 1
            } else {
                previousChar = src[sourceLength - 1]
                isEof = true
            }
            return key
        }
        val builder = StringBuilder()
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t', KeySeparator, KeyValueSeparator, EndArray -> {
                    break
                }
                '\n' -> {
                    throwIncomplete()
                }
                '\r' -> {
                    throwUnexpectedToken(current)
                }
                else -> {
                    // Validate each character as it is read; the caller only
                    // enters here on a bare-key character, so the result is
                    // never empty (matching the former `[A-Za-z0-9_-]+` regex).
                    throwUnexpectedTokenIf(current) { it !in BareKeyConstraints }
                    builder.append(current)
                    proceed()
                }
            }
        }
        return builder.toString()
    }

    /**
     * Start right on the first '\"', end right after the last '\"'.
     */
    private fun parseStringKey(): String {
        val content = parseStringValue().content
        // A key is a single-line string: a multiline """...""" is not a valid key.
        throwUnexpectedTokenIf('\"') { lastStringMultiline }
        return content
    }

    /**
     * Start right on the first '\'', end right after the last '\''.
     */
    private fun parseLiteralStringKey(): String {
        val content = parseLiteralStringValue().content
        // A key is a single-line string: a multiline '''...''' is not a valid key.
        throwUnexpectedTokenIf('\'') { lastStringMultiline }
        return content
    }

    /**
     * Start right on the actual token, end right on '\n' or ',' or ']' or '}'.
     */
    private fun parseValue(isInsideStructure: Boolean): TomlElement {
        lastStringMultiline = false
        lastStringLiteral = false
        lastInlineComment = null
        var element: TomlElement? = null
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    break
                }
                '\r' -> {
                    proceed()
                    throwUnexpectedTokenIf(current) { isEof || getCurrent() != '\n' }
                }
                Comment -> {
                    // A comment after a top-level value is its inline comment;
                    // comments inside arrays/inline tables are not yet captured.
                    val text = parseComment()
                    if (!isInsideStructure) {
                        lastInlineComment = text
                    }
                }
                ElementSeparator, EndArray, EndInlineTable -> {
                    throwUnexpectedTokenIf(current) { !isInsideStructure }
                    break
                }
                else -> {
                    throwUnexpectedTokenIf(current) { element != null }
                    element = when (current) {
                        't', 'f' -> {
                            parseBooleanValue()
                        }
                        in '0'..'9' -> {
                            parseNumberOrDateTimeValue(sign = null)
                        }
                        'i' -> {
                            parseSpecialNumberValue(sign = null)
                        }
                        'n' -> {
                            proceed()
                            throwIncompleteIf { isEof }
                            when (val second = getCurrent()) {
                                'a' -> {
                                    parseSpecialNumberValue(sign = null)
                                }
                                'u' -> {
                                    parseNullValue()
                                }
                                else -> {
                                    throwUnexpectedToken(second)
                                }
                            }
                        }
                        '+', '-' -> {
                            proceed()
                            throwIncompleteIf { isEof }
                            when (val second = getCurrent()) {
                                in '0'..'9' -> {
                                    // Pretend it could be a date time.
                                    parseNumberOrDateTimeValue(current)
                                }
                                'i' -> {
                                    parseSpecialNumberValue(current)
                                }
                                'n' -> {
                                    // parseSpecialNumberValue starts on 'a'.
                                    proceed()
                                    throwIncompleteIf { isEof }
                                    parseSpecialNumberValue(current)
                                }
                                else -> {
                                    throwUnexpectedToken(second)
                                }
                            }
                        }
                        '\"' -> {
                            parseStringValue()
                        }
                        '\'' -> {
                            parseLiteralStringValue()
                        }
                        StartArray -> {
                            parseArrayValue()
                        }
                        StartInlineTable -> {
                            parseInlineTableValue()
                        }
                        else -> {
                            throwUnexpectedToken(current)
                        }
                    }
                }
            }
        }
        throwIncompleteIf { element == null }
        return element!!
    }

    /**
     * Start right on 't' or 'f', ends right after the last token.
     */
    private fun parseBooleanValue(): TomlLiteral {
        return when (val current = getCurrent()) {
            't' -> {
                expectNext("rue")
                proceed()
                TomlLiteral(true)
            }
            'f' -> {
                expectNext("alse")
                proceed()
                TomlLiteral(false)
            }
            else -> {
                throwUnexpectedToken(current)
            }
        }
    }

    /**
     * Start right on 'i' or 'a', end right after the last token.
     */
    private fun parseSpecialNumberValue(sign: Char?): TomlLiteral {
        return when (val current = getCurrent()) {
            'i' -> {
                val content = if (sign == null) "inf" else sign.toString() + "inf"
                expectNext("nf")
                proceed()
                TomlLiteral(content, Type.Float)
            }
            'a' -> {
                expectNext('n')
                proceed()
                TomlLiteral("nan", Type.Float)
            }
            else -> {
                throwUnexpectedToken(current)
            }
        }
    }

    /**
     * Start right on the first number, end right on the second ' ' (if this
     * is a date time), '\t', '\n', ',', '#', ']', '}'.
     */
    private fun parseNumberOrDateTimeValue(sign: Char?): TomlLiteral {
        val builder = StringBuilder()
        var leadingZero = false
        if (getCurrent() == '0') {
            proceed()
            if (isEof) {
                val content = if (sign == null) "0" else sign.toString() + "0"
                return TomlLiteral(content, Type.Integer)
            }
            when (val current = getCurrent()) {
                'x', 'b', 'o' -> {
                    // A leading sign is not allowed on hex/octal/binary integers.
                    throwUnexpectedTokenIf(current) { sign != null }
                    val radix = when (current) {
                        'x' -> 16
                        'b' -> 2
                        else -> 8
                    }
                    proceed()
                    return parseNumberValue(builder, radix, sign)
                }
                else -> {
                    builder.append('0')
                    // A leading zero is only valid as the bare integer 0 or the
                    // 0 before a fraction/exponent (0.5, 0e1). Another digit or
                    // underscore after it means a leading-zero number -- unless
                    // it turns out to be a zero-padded date-time field, which is
                    // checked once the value's kind is known (see below).
                    leadingZero = current in '0'..'9' || current == '_'
                }
            }
        }
        var isNumber = true
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t', '\n', ElementSeparator, Comment, EndArray, EndInlineTable -> {
                    break
                }
                '\r' -> {
                    proceed()
                    throwUnexpectedTokenIf(current) { isEof || getCurrent() != '\n' }
                }
                in '0'..'9' -> {
                    builder.append(current)
                    proceed()
                }
                '-', '+' -> {
                    val previous = getPrevious()
                    if (previous != 'e' || previous != 'E') {
                        throwUnexpectedTokenIf(current) { sign != null }
                        isNumber = false
                    }
                    break
                }
                in DefiniteDateTimeConstraints -> {
                    throwUnexpectedTokenIf(current) { sign != null }
                    isNumber = false
                    break
                }
                in DefiniteNumberConstraints -> {
                    break
                }
                else -> {
                    throwUnexpectedToken(current)
                }
            }
        }
        return if (isNumber) {
            // Now that we know it is a number (not a zero-padded date-time), a
            // leading zero is illegal.
            if (leadingZero) {
                throwUnexpectedToken('0')
            }
            parseNumberValue(builder, radix = 10, sign)
        } else {
            parseDateTimeValue(builder)
        }
    }

    /**
     * Start right on the first token that does not belong to a date time, end
     * right on ' ', '\t', '\n', ',', '#', ']', '}'.
     */
    private fun parseNumberValue(
        builder: StringBuilder,
        radix: Int,
        sign: Char?
    ): TomlLiteral {
        var isDouble = false
        var isExponent = false
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t', '\n', ElementSeparator, Comment, EndArray, EndInlineTable -> {
                    break
                }
                '\r' -> {
                    proceed()
                    throwUnexpectedTokenIf(current) { isEof || getCurrent() != '\n' }
                }
                '0', '1' -> {
                    builder.append(current)
                    proceed()
                }
                '2', '3', '4', '5', '6', '7' -> {
                    throwUnexpectedTokenIf(current) { radix == 2 }
                    builder.append(current)
                    proceed()
                }
                '8', '9' -> {
                    throwUnexpectedTokenIf(current) { radix <= 8 }
                    builder.append(current)
                    proceed()
                }
                '.' -> {
                    throwUnexpectedTokenIf(current) {
                        isDouble || isExponent || radix != 10 || !getPrevious().isDecimalDigit()
                    }
                    proceed()
                    throwIncompleteIf { isEof }
                    // Urge check.
                    val next = getCurrent()
                    throwUnexpectedTokenIf(next) { !it.isDecimalDigit() }
                    builder.append(current).append(next)
                    isDouble = true
                    proceed()
                }
                'e', 'E' -> {
                    when (radix) {
                        10 -> {
                            throwUnexpectedTokenIf(current) {
                                isExponent || !getPrevious().isDecimalDigit()
                            }
                            proceed()
                            throwIncompleteIf { isEof }
                            // Urge check.
                            val next = getCurrent()
                            throwUnexpectedTokenIf(next) { !it.isDecimalOrSign() }
                            builder.append(current).append(next)
                            isExponent = true
                            if (next == '-') {
                                isDouble = true
                            }
                            if (next == '+' || next == '-') {
                                // A sign must be followed by at least one digit;
                                // "1e+" / "1e-" are not valid exponents.
                                proceed()
                                throwIncompleteIf { isEof }
                                val digit = getCurrent()
                                throwUnexpectedTokenIf(digit) { !it.isDecimalDigit() }
                                builder.append(digit)
                            }
                        }
                        16 -> {
                            builder.append(current)
                        }
                        else -> {
                            throwUnexpectedToken(current)
                        }
                    }
                    proceed()
                }
                'a', 'b', 'c', 'd', 'f', 'A', 'B', 'C', 'D', 'F' -> {
                    throwUnexpectedTokenIf(current) { radix <= 10 }
                    builder.append(current)
                    proceed()
                }
                '_' -> {
                    // An underscore must sit between two digits, so it cannot be
                    // the first character after a 0x/0o/0b prefix (builder empty).
                    throwUnexpectedTokenIf(current) { builder.isEmpty() || !getPrevious().isHexDigit() }
                    proceed()
                    throwIncompleteIf { isEof }
                    // Urge check.
                    val next = getCurrent()
                    throwUnexpectedTokenIf(next) { !it.isHexDigit() }
                }
                else -> {
                    throwUnexpectedToken(current)
                }
            }
        }
        val result = builder.toString()
        return createNumberTomlLiteral(
            content = result,
            isPositive = sign != '-',
            radix = radix,
            isDouble = isDouble,
            isExponent = isExponent
        )
    }

    /**
     * Start right on '-', ':', end right on the second ' ', '\t', '\n', ',',
     * '#', ']', '}'.
     */
    private fun parseDateTimeValue(builder: StringBuilder): TomlLiteral {
        var hasDate = false
        var hasTime = false
        var hasOffset = false
        var hasDateTimeSeparator = false
        while (!isEof) {
            when (val current = getCurrent()) {
                '\t', '\n', ElementSeparator, Comment, EndArray, EndInlineTable -> {
                    break
                }
                '\r' -> {
                    proceed()
                    throwUnexpectedTokenIf(current) { isEof || getCurrent() != '\n' }
                }
                ' ' -> {
                    // A space after a date is the date-time separator only if a
                    // time follows it. Otherwise this is a local date and the
                    // space just precedes a comment or the end of the value.
                    if (hasDate && !hasDateTimeSeparator) {
                        proceed()
                        if (!isEof && getCurrent() in '0'..'9') {
                            hasDateTimeSeparator = true
                            builder.append('T')
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
                in '0'..'9' -> {
                    builder.append(current)
                    proceed()
                }
                '-' -> {
                    if (!hasTime) {
                        hasDate = true
                        builder.append(current)
                        proceed()
                    } else {
                        appendNumericOffset(builder, current)
                        hasOffset = true
                    }
                }
                'T', 't' -> {
                    hasDateTimeSeparator = true
                    builder.append('T')
                    proceed()
                }
                ':' -> {
                    hasTime = true
                    builder.append(current)
                    proceed()
                }
                '.' -> {
                    // The fractional-second dot must be followed by a digit, so
                    // "09:09:09." and "09:09:09.Z" are rejected.
                    builder.append(current)
                    proceed()
                    throwIncompleteIf { isEof }
                    throwUnexpectedTokenIf(getCurrent()) { !it.isDecimalDigit() }
                }
                'Z', 'z' -> {
                    hasOffset = true
                    builder.append('Z')
                    proceed()
                }
                '+' -> {
                    appendNumericOffset(builder, current)
                    hasOffset = true
                }
                else -> {
                    throwUnexpectedToken(current)
                }
            }
        }
        val result = builder.toString()
        val type = when {
            hasDate && hasTime -> {
                if (!hasOffset) {
                    NativeLocalDateTime(result)
                    Type.LocalDateTime
                } else {
                    NativeOffsetDateTime(result)
                    Type.OffsetDateTime
                }
            }
            hasDate -> {
                NativeLocalDate(result)
                Type.LocalDate
            }
            hasTime -> {
                NativeLocalTime(result)
                Type.LocalTime
            }
            else -> {
                error("Malformed date time: $result")
            }
        }
        // Keeps the original text.
        return TomlLiteral(result, type)
    }

    // Consumes a numeric UTC offset of the exact form (+|-)HH:MM, starting on the
    // [sign]. A bare-hour offset like "+09" (no minutes) is rejected.
    private fun appendNumericOffset(builder: StringBuilder, sign: Char) {
        builder.append(sign)
        proceed()
        appendTwoDigits(builder)
        throwIncompleteIf { isEof }
        throwUnexpectedTokenIf(getCurrent()) { it != ':' }
        builder.append(':')
        proceed()
        appendTwoDigits(builder)
    }

    private fun appendTwoDigits(builder: StringBuilder) {
        repeat(2) {
            throwIncompleteIf { isEof }
            val digit = getCurrent()
            throwUnexpectedTokenIf(digit) { !it.isDecimalDigit() }
            builder.append(digit)
            proceed()
        }
    }

    /**
     * Start right on the first '\"', end right after the last '\"'.
     */
    private fun parseStringValue(): TomlLiteral {
        proceed()
        throwIncompleteIf { isEof }
        val builder = StringBuilder()
        val initialSecondChar = getCurrent()
        val multiline: Boolean
        if (initialSecondChar != '\"') {
            multiline = false
        } else {
            proceed()
            if (!isEof && getCurrent() == '\"') {
                multiline = true
                proceed()
                throwIncompleteIf { isEof }
                if (getCurrent() == '\r') {
                    proceed()
                    throwIncompleteIf { isEof }
                    if (getCurrent() != '\n') {
                        builder.append('\r')
                    }
                }
                if (getCurrent() == '\n') {
                    // Consumes the initial line feed
                    currentLineNumber++
                    proceed()
                }
            } else {
                return TomlLiteral("")
            }
        }
        lastStringMultiline = multiline
        var trim = false
        // A line-ending backslash followed by spaces/tabs (rather than an
        // immediate newline) is only valid if those run all the way to a line
        // break: any other content before the newline makes "\<space>" an
        // invalid escape. While this is set, content before a newline is an
        // error.
        var requireNewlineBeforeContent = false
        var hasEscape = false
        var justEnded = false
        while (!isEof) {
            // Fast path: when the input is in memory, bulk-copy a run of plain
            // content characters instead of appending one char per loop turn.
            // Stops at anything needing special handling, which the `when` below
            // then processes a character at a time.
            val src = source
            if (src != null && !trim) {
                val begin = sourceIndex - 1
                var end = begin
                while (end < sourceLength) {
                    val c = src[end]
                    if (c == '\"' || c == '\\' || c == '\r' || c == '\n' || c.isForbiddenControlChar()) {
                        break
                    }
                    end++
                }
                if (end > begin) {
                    builder.appendRange(src, begin, end)
                    if (end < sourceLength) {
                        previousChar = src[end - 1]
                        currentChar = src[end]
                        sourceIndex = end + 1
                    } else {
                        previousChar = src[sourceLength - 1]
                        isEof = true
                    }
                    continue
                }
            }
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    if (!trim) {
                        builder.append(current)
                    }
                    proceed()
                }
                '\n' -> {
                    throwIncompleteIf { !multiline }
                    if (!trim) {
                        builder.append(current)
                    }
                    // The line break satisfies a pending line-ending backslash.
                    requireNewlineBeforeContent = false
                    currentLineNumber++
                    proceed()
                }
                '\r' -> {
                    // A carriage return is only allowed as part of a CRLF.
                    proceed()
                    throwIncompleteIf { isEof }
                    throwUnexpectedTokenIf(current) { getCurrent() != '\n' }
                    // The CRLF satisfies a pending line-ending backslash.
                    requireNewlineBeforeContent = false
                }
                '\"' -> {
                    // A closing delimiter (or literal quote) is content, so it
                    // cannot satisfy a pending line-ending backslash.
                    throwUnexpectedTokenIf(current) { requireNewlineBeforeContent }
                    if (!multiline) {
                        justEnded = true
                        proceed()
                        break
                    }
                    // Count the run of consecutive quotes. The closing
                    // delimiter is the final three; up to two quotes right
                    // before them are literal content (more than two is
                    // invalid).
                    var quoteCount = 0
                    while (!isEof && getCurrent() == '\"') {
                        quoteCount++
                        proceed()
                    }
                    if (quoteCount >= 3) {
                        throwUnexpectedTokenIf(current) { quoteCount - 3 > 2 }
                        repeat(quoteCount - 3) { builder.append('\"') }
                        justEnded = true
                        break
                    }
                    trim = false
                    repeat(quoteCount) { builder.append('\"') }
                }
                '\\' -> {
                    // A backslash escape is content; if a line-ending backslash
                    // is still waiting for its newline this is invalid.
                    throwUnexpectedTokenIf(current) { requireNewlineBeforeContent }
                    proceed()
                    throwIncompleteIf { isEof }
                    when (val next = getCurrent()) {
                        ' ', '\t', '\n', '\r' -> {
                            throwUnexpectedTokenIf(current) { !multiline }
                            trim = true
                            // When the backslash is followed by spaces/tabs
                            // rather than the newline itself, those must lead to
                            // a line break before any further content.
                            if (next == ' ' || next == '\t') {
                                requireNewlineBeforeContent = true
                            }
                        }
                        'n', '\"', '\\', 'u', 'U', 'x', 't', 'r', 'b', 'e', 'f' -> {
                            // 'x' (\xHH) and 'e' (\e) are TOML 1.1 additions.
                            builder.append(current).append(next)
                            hasEscape = true
                            proceed()
                        }
                        else -> {
                            throwUnexpectedToken(current)
                        }
                    }
                }
                else -> {
                    throwUnexpectedTokenIf(current) { it.isForbiddenControlChar() }
                    // Plain content cannot satisfy a pending line-ending
                    // backslash either.
                    throwUnexpectedTokenIf(current) { requireNewlineBeforeContent }
                    builder.append(current)
                    trim = false
                    proceed()
                }
            }
        }
        throwIncompleteIf { !justEnded }
        val result = builder.toString()
        // Only walk the string again to unescape when an escape was actually
        // seen; plain strings (the common case) are returned as-is.
        val content = if (hasEscape) result.unescape() else result
        return TomlLiteral(content)
    }

    /**
     * Start right on the first '\'', end right after the last '\''.
     */
    private fun parseLiteralStringValue(): TomlLiteral {
        lastStringLiteral = true
        proceed()
        throwIncompleteIf { isEof }
        val builder = StringBuilder()
        val initialSecondChar = getCurrent()
        val multiline: Boolean
        if (initialSecondChar != '\'') {
            multiline = false
        } else {
            proceed()
            if (!isEof && getCurrent() == '\'') {
                multiline = true
                proceed()
                throwIncompleteIf { isEof }
                if (getCurrent() == '\r') {
                    proceed()
                    throwIncompleteIf { isEof }
                    if (getCurrent() != '\n') {
                        builder.append('\r')
                    }
                }
                if (getCurrent() == '\n') {
                    // Consumes the initial line feed.
                    currentLineNumber++
                    proceed()
                }
            } else {
                return TomlLiteral("")
            }
        }
        lastStringMultiline = multiline
        var justEnded = false
        while (!isEof) {
            // Fast path: when the input is in memory, bulk-copy a run of plain
            // content characters at once. A literal string has no escapes, so
            // only the delimiter, line breaks and control characters stop it.
            val src = source
            if (src != null) {
                val begin = sourceIndex - 1
                var end = begin
                while (end < sourceLength) {
                    val c = src[end]
                    if (c == '\'' || c == '\r' || c == '\n' || c.isForbiddenControlChar()) {
                        break
                    }
                    end++
                }
                if (end > begin) {
                    builder.appendRange(src, begin, end)
                    if (end < sourceLength) {
                        previousChar = src[end - 1]
                        currentChar = src[end]
                        sourceIndex = end + 1
                    } else {
                        previousChar = src[sourceLength - 1]
                        isEof = true
                    }
                    continue
                }
            }
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    builder.append(current)
                    proceed()
                }
                '\n' -> {
                    throwIncompleteIf { !multiline }
                    builder.append(current)
                    currentLineNumber++
                    proceed()
                }
                '\r' -> {
                    // A carriage return is only allowed as part of a CRLF.
                    proceed()
                    throwIncompleteIf { isEof }
                    throwUnexpectedTokenIf(current) { getCurrent() != '\n' }
                }
                '\'' -> {
                    if (!multiline) {
                        justEnded = true
                        proceed()
                        break
                    }
                    // Count the run of consecutive apostrophes. The closing
                    // delimiter is the final three; up to two right before
                    // them are literal content (more than two is invalid).
                    var quoteCount = 0
                    while (!isEof && getCurrent() == '\'') {
                        quoteCount++
                        proceed()
                    }
                    if (quoteCount >= 3) {
                        throwUnexpectedTokenIf(current) { quoteCount - 3 > 2 }
                        repeat(quoteCount - 3) { builder.append('\'') }
                        justEnded = true
                        break
                    }
                    repeat(quoteCount) { builder.append('\'') }
                }
                else -> {
                    throwUnexpectedTokenIf(current) { it.isForbiddenControlChar() }
                    builder.append(current)
                    proceed()
                }
            }
        }
        throwIncompleteIf { !justEnded }
        val result = builder.toString()
        return TomlLiteral(result)
    }

    /**
     * Start right on '[', end right after ']'.
     */
    private fun parseArrayValue(): TomlArray {
        proceed()
        val builder = mutableListOf<TomlElement>()
        var expectValue = true
        var justEnded = false
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    currentLineNumber++
                    proceed()
                }
                '\r' -> {
                    proceed()
                    throwIncompleteIf { isEof }
                    throwUnexpectedTokenIf(current) { getCurrent() != '\n' }
                }
                EndArray -> {
                    justEnded = true
                    proceed()
                    break
                }
                ElementSeparator -> {
                    throwUnexpectedTokenIf(current) { expectValue }
                    expectValue = true
                    proceed()
                }
                Comment -> {
                    parseComment()
                }
                else -> {
                    val value = parseValue(isInsideStructure = true)
                    builder.add(value)
                    expectValue = false
                }
            }
        }
        throwIncompleteIf { !justEnded }
        return TomlArray(builder)
    }

    /**
     * Start right on '{', end right after '}'.
     */
    private fun parseInlineTableValue(): TomlTable {
        proceed()
        val builder = KeyNode("", isLast = false)
        var expectEntry = true
        var justEnded = false
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    // TOML 1.1 allows newlines inside inline tables.
                    currentLineNumber++
                    proceed()
                }
                '\r' -> {
                    proceed()
                    throwIncompleteIf { isEof }
                    throwUnexpectedTokenIf(current) { getCurrent() != '\n' }
                }
                EndInlineTable -> {
                    // An empty table or a trailing comma before '}' is allowed;
                    // a leading or doubled comma is rejected by the ',' arm.
                    justEnded = true
                    proceed()
                    break
                }
                ElementSeparator -> {
                    throwUnexpectedTokenIf(current) { expectEntry }
                    expectEntry = true
                    proceed()
                }
                Comment -> {
                    // TOML 1.1 allows newlines inside inline tables, and so
                    // comments wherever a newline may appear. The comment text
                    // is not retained.
                    parseComment()
                }
                else -> {
                    val localPath = parsePath()
                    throwUnexpectedTokenIf(getCurrent()) { it != KeyValueSeparator }
                    proceed()
                    val key = localPath.last()
                    val value = parseValue(isInsideStructure = true)
                    val node = ValueNode(key, value)
                    if (builder.addByPath(localPath, node, null).not()) {
                        throwConflictEntry(localPath)
                    }
                    expectEntry = false
                }
            }
        }
        throwIncompleteIf { !justEnded }
        return TomlTable(builder)
    }

    /**
     * Start right on 'u', end right after the second 'l'.
     */
    private fun parseNullValue(): TomlNull {
        expectNext("ll")
        proceed()
        return TomlNull
    }

    /**
     * Start right on '#', end right on '\n'. Returns the comment text with a
     * single leading space removed, matching how the emitter renders comments.
     */
    private fun parseComment(): String {
        proceed()
        val builder = StringBuilder()
        while (!isEof) {
            when (val current = getCurrent()) {
                '\n' -> {
                    break
                }
                '\r' -> {
                    // A carriage return is only allowed as part of a CRLF.
                    proceed()
                    throwUnexpectedTokenIf(current) { isEof || getCurrent() != '\n' }
                    break
                }
                else -> {
                    throwUnexpectedTokenIf(current) { it.isForbiddenControlChar() }
                    builder.append(current)
                    proceed()
                }
            }
        }
        return builder.toString().removePrefix(" ")
    }

    private fun takeEntryAnnotations(value: TomlElement?): List<Annotation> {
        val hasComment = pendingComments.isNotEmpty()
        val inlineComment = lastInlineComment
        lastInlineComment = null
        val styledString = value is TomlLiteral && value.type == Type.String &&
            (lastStringMultiline || lastStringLiteral)
        if (!hasComment && inlineComment == null && !styledString) {
            return emptyList()
        }
        val annotations = mutableListOf<Annotation>()
        if (hasComment) {
            annotations.add(TomlComment(pendingComments.toString()))
            pendingComments.clear()
        }
        if (inlineComment != null) {
            annotations.add(TomlComment(inlineComment, inline = true))
        }
        if (styledString) {
            if (lastStringMultiline) {
                annotations.add(TomlMultilineString.Instance)
            }
            if (lastStringLiteral) {
                annotations.add(TomlLiteralString.Instance)
            }
        }
        return annotations
    }
}
