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
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlArray
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlLiteral.Type
import dev.eav.tomlkt.TomlNull
import dev.eav.tomlkt.TomlReader
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.internal.BareKeyConstraints
import dev.eav.tomlkt.internal.BareKeyRegex
import dev.eav.tomlkt.internal.Comment
import dev.eav.tomlkt.internal.DecimalConstraints
import dev.eav.tomlkt.internal.DecimalOrSignConstraints
import dev.eav.tomlkt.internal.DefiniteDateTimeConstraints
import dev.eav.tomlkt.internal.DefiniteNumberConstraints
import dev.eav.tomlkt.internal.ElementSeparator
import dev.eav.tomlkt.internal.EndArray
import dev.eav.tomlkt.internal.EndInlineTable
import dev.eav.tomlkt.internal.EndTableHead
import dev.eav.tomlkt.internal.HexadecimalConstraints
import dev.eav.tomlkt.internal.KeySeparator
import dev.eav.tomlkt.internal.KeyValueSeparator
import dev.eav.tomlkt.internal.Path
import dev.eav.tomlkt.internal.StartArray
import dev.eav.tomlkt.internal.StartInlineTable
import dev.eav.tomlkt.internal.StartTableHead
import dev.eav.tomlkt.internal.createNumberTomlLiteral
import dev.eav.tomlkt.internal.throwConflictEntry
import dev.eav.tomlkt.internal.throwIncomplete
import dev.eav.tomlkt.internal.throwUnexpectedToken
import dev.eav.tomlkt.internal.unescape
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class TomlElementParser(
    private val toml: Toml,
    private val reader: TomlReader
) {
    private var currentChar: Char = 0.toChar()

    private var previousChar: Char = 0.toChar()

    private var currentLineNumber: Int = 1

    private var isEof: Boolean = false

    // region Read

    private fun proceed() {
        if (isEof) {
            return
        }
        val code = reader.read()
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
                    val path = if (currentTablePath != null) currentTablePath + localPath else localPath
                    if (tree.addByPath(path, node, arrayOfTableIndices).not()) {
                        throwConflictEntry(path)
                    }
                }
                Comment -> {
                    parseComment()
                }
                StartTableHead -> {
                    proceed()
                    throwIncompleteIf { isEof }
                    val isArrayOfTable = getCurrent() == StartTableHead
                    if (isArrayOfTable) {
                        proceed()
                    }
                    val path = parseTableHead(isArrayOfTable)
                    if (!isArrayOfTable) {
                        val key = path.last()
                        val node = KeyNode(key, isLast = true)
                        if (tree.addByPath(path, node, arrayOfTableIndices).not()) {
                            throwConflictEntry(path)
                        }
                    } else {
                        val iterator = arrayOfTableIndices.keys.iterator()
                        for (key in iterator) {
                            if (key.size > path.size && key.subList(0, path.size) == path) {
                                iterator.remove()
                            }
                        }
                        val currentIndex = arrayOfTableIndices[path]
                        if (currentIndex == null) {
                            arrayOfTableIndices[path] = 0
                            val key = path.last()
                            val node = ArrayNode(key)
                            if (tree.addByPath(path, node, arrayOfTableIndices).not()) {
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
                    builder.append(current)
                    proceed()
                }
            }
        }
        val result = builder.toString()
        if (BareKeyRegex.matches(result).not()) { // Lazy check.
            val unexpectedTokens = result.filterNot(BareKeyConstraints::contains)
            throwUnexpectedToken(unexpectedTokens[0])
        }
        return result
    }

    /**
     * Start right on the first '\"', end right after the last '\"'.
     */
    private fun parseStringKey(): String {
        return parseStringValue().content
    }

    /**
     * Start right on the first '\'', end right after the last '\''.
     */
    private fun parseLiteralStringKey(): String {
        return parseLiteralStringValue().content
    }

    /**
     * Start right on the actual token, end right on '\n' or ',' or ']' or '}'.
     */
    private fun parseValue(isInsideStructure: Boolean): TomlElement {
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
                    parseComment()
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
                        in DecimalConstraints -> {
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
                                in DecimalConstraints -> {
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
        if (getCurrent() == '0') {
            proceed()
            if (isEof) {
                val content = if (sign == null) "0" else sign.toString() + "0"
                return TomlLiteral(content, Type.Integer)
            }
            when (getCurrent()) {
                'x' -> {
                    proceed()
                    return parseNumberValue(builder, radix = 16, sign)
                }
                'b' -> {
                    proceed()
                    return parseNumberValue(builder, radix = 2, sign)
                }
                'o' -> {
                    proceed()
                    return parseNumberValue(builder, radix = 8, sign)
                }
                else -> {
                    builder.append('0')
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
                in DecimalConstraints -> {
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
                        isDouble || isExponent || radix != 10 || getPrevious() !in DecimalConstraints
                    }
                    proceed()
                    throwIncompleteIf { isEof }
                    // Urge check.
                    val next = getCurrent()
                    throwUnexpectedTokenIf(next) { it !in DecimalConstraints }
                    builder.append(current).append(next)
                    isDouble = true
                    proceed()
                }
                'e', 'E' -> {
                    when (radix) {
                        10 -> {
                            throwUnexpectedTokenIf(current) {
                                isExponent || getPrevious() !in DecimalConstraints
                            }
                            proceed()
                            throwIncompleteIf { isEof }
                            // Urge check.
                            val next = getCurrent()
                            throwUnexpectedTokenIf(next) { it !in DecimalOrSignConstraints }
                            builder.append(current).append(next)
                            isExponent = true
                            if (next == '-') {
                                isDouble = true
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
                    throwUnexpectedTokenIf(current) { getPrevious() !in HexadecimalConstraints }
                    proceed()
                    throwIncompleteIf { isEof }
                    // Urge check.
                    val next = getCurrent()
                    throwUnexpectedTokenIf(next) { it !in HexadecimalConstraints }
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
                    if (hasDate && !hasDateTimeSeparator) {
                        hasDateTimeSeparator = true
                        builder.append('T')
                        proceed()
                    } else {
                        break
                    }
                }
                in DecimalConstraints -> {
                    builder.append(current)
                    proceed()
                }
                '-' -> {
                    if (!hasTime) {
                        hasDate = true
                    } else {
                        hasOffset = true
                    }
                    builder.append(current)
                    proceed()
                }
                'T', 't' -> {
                    hasDateTimeSeparator = true
                    builder.append('T')
                    proceed()
                }
                ':' -> {
                    if (!hasOffset) {
                        hasTime = true
                    }
                    builder.append(current)
                    proceed()
                }
                '.' -> {
                    builder.append(current)
                    proceed()
                }
                'Z', 'z' -> {
                    hasOffset = true
                    builder.append('Z')
                    proceed()
                }
                '+' -> {
                    hasOffset = true
                    builder.append(current)
                    proceed()
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
        var trim = false
        var justEnded = false
        while (!isEof) {
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
                    currentLineNumber++
                    proceed()
                }
                '\r' -> {
                    proceed()
                    throwIncompleteIf { isEof }
                    if (getCurrent() != '\n') {
                        builder.append(current)
                    }
                }
                '\"' -> {
                    if (!multiline) {
                        justEnded = true
                        proceed()
                        break
                    }
                    proceed()
                    throwIncompleteIf { isEof }
                    val second = getCurrent()
                    if (second != '\"') {
                        builder.append(current)
                        continue
                    }
                    proceed()
                    throwIncompleteIf { isEof }
                    if (getCurrent() != '\"') {
                        builder.append(current).append(second)
                        continue
                    }
                    justEnded = true
                    proceed()
                    break
                }
                '\\' -> {
                    proceed()
                    throwIncompleteIf { isEof }
                    when (val next = getCurrent()) {
                        ' ', '\t', '\n' -> {
                            throwUnexpectedTokenIf(current) { !multiline }
                            trim = true
                        }
                        'n', '\"', '\\', 'u', 'U', 't', 'r', 'b', 'f' -> {
                            builder.append(current).append(next)
                            proceed()
                        }
                        else -> {
                            throwUnexpectedToken(current)
                        }
                    }
                }
                else -> {
                    builder.append(current)
                    trim = false
                    proceed()
                }
            }
        }
        throwIncompleteIf { !justEnded }
        val result = builder.toString()
        val content = result.unescape()
        return TomlLiteral(content)
    }

    /**
     * Start right on the first '\'', end right after the last '\''.
     */
    private fun parseLiteralStringValue(): TomlLiteral {
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
        var justEnded = false
        while (!isEof) {
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
                    proceed()
                    throwIncompleteIf { isEof }
                    if (getCurrent() != '\n') {
                        builder.append(current)
                    }
                }
                '\'' -> {
                    if (!multiline) {
                        justEnded = true
                        proceed()
                        break
                    }
                    proceed()
                    throwIncompleteIf { isEof }
                    val second = getCurrent()
                    if (second != '\'') {
                        builder.append(current)
                        continue
                    }
                    proceed()
                    throwIncompleteIf { isEof }
                    if (getCurrent() != '\'') {
                        builder.append(current).append(second)
                        continue
                    }
                    justEnded = true
                    proceed()
                    break
                }
                else -> {
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
        var justStarted = true
        var justEnded = false
        while (!isEof) {
            when (val current = getCurrent()) {
                ' ', '\t' -> {
                    proceed()
                }
                '\n' -> {
                    throwIncomplete()
                }
                EndInlineTable -> {
                    throwUnexpectedTokenIf(current) { expectEntry && !justStarted }
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
                    throwUnexpectedToken(current)
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
                    justStarted = false
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
     * Start right on '#', end right on '\n'.
     */
    private fun parseComment() {
        proceed()
        while (!isEof) {
            if (getCurrent() == '\n') {
                break
            }
            proceed()
        }
    }
}
