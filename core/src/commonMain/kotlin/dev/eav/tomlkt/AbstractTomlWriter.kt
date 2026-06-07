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

package dev.eav.tomlkt

import dev.eav.tomlkt.TomlInteger.Base
import dev.eav.tomlkt.TomlInteger.Base.Dec
import dev.eav.tomlkt.internal.Comment
import dev.eav.tomlkt.internal.ElementSeparator
import dev.eav.tomlkt.internal.EndArray
import dev.eav.tomlkt.internal.EndInlineTable
import dev.eav.tomlkt.internal.EndTableHead
import dev.eav.tomlkt.internal.KeySeparator
import dev.eav.tomlkt.internal.KeyValueSeparator
import dev.eav.tomlkt.internal.StartArray
import dev.eav.tomlkt.internal.StartInlineTable
import dev.eav.tomlkt.internal.StartTableHead
import dev.eav.tomlkt.internal.doubleQuoted
import dev.eav.tomlkt.internal.escape
import dev.eav.tomlkt.internal.isBareKey
import dev.eav.tomlkt.internal.processIntegerString
import dev.eav.tomlkt.internal.singleQuoted
import dev.eav.tomlkt.internal.toStringModified

/**
 * The basic implementation of [TomlWriter], handling all the essential logic
 * except [writeString] and (optional) [writeChar].
 */
public abstract class AbstractTomlWriter : TomlWriter {
    // -------- Core --------

    // Better implementation is welcomed.
    override fun writeChar(char: Char) {
        writeString(char.toString())
    }

    // -------- Key --------

    final override fun writeKey(key: String) {
        // A bare key needs neither escaping nor quoting, so skip both. This is
        // the common case; only keys with non-bare characters pay for the
        // StringBuilder in escape() and the surrounding quotes.
        if (key.isBareKey()) {
            writeString(key)
        } else {
            writeString(key.escape().doubleQuoted)
        }
    }

    final override fun writeKeySeparator() {
        writeChar(KeySeparator)
    }

    // -------- Table Head --------

    final override fun startRegularTableHead() {
        writeChar(StartTableHead)
    }

    final override fun endRegularTableHead() {
        writeChar(EndTableHead)
    }

    final override fun startArrayOfTableHead() {
        writeChar(StartTableHead)
        writeChar(StartTableHead)
    }

    final override fun endArrayOfTableHead() {
        writeChar(EndTableHead)
        writeChar(EndTableHead)
    }

    // -------- Value --------

    final override fun writeBooleanValue(boolean: Boolean) {
        writeString(boolean.toString())
    }

    final override fun writeIntegerValue(integer: Long, base: Base, group: Int, uppercase: Boolean) {
        require(group >= 0) { "Group size cannot be negative" }
        require(integer >= 0L || base == Dec) {
            "Negative integer cannot be represented by other bases, but found $integer"
        }
        val string = processIntegerString(
            raw = integer.toString(base.value),
            base = base,
            group = group,
            uppercase = uppercase
        )
        writeString(string)
    }

    final override fun writeFloatValue(float: Double) {
        writeString(float.toStringModified())
    }

    final override fun writeStringValue(string: String, isMultiline: Boolean, isLiteral: Boolean) {
        when {
            !isMultiline && !isLiteral -> {
                writeString(string.escape().doubleQuoted)
            }
            !isMultiline -> {
                require('\'' !in string && '\n' !in string) {
                    "Cannot have '\\'' or '\\n' in literal string, but found $string"
                }
                writeString(string.singleQuoted)
            }
            !isLiteral -> {
                writeString("\"\"\"")
                writeLineFeed()
                writeString(string.escape(multiline = true))
                writeString("\"\"\"")
            }
            else -> {
                require("\'\'\'" !in string) {
                    "Cannot have \"\\'\\'\\'\" in multiline literal string, but found $string"
                }
                writeString("\'\'\'")
                writeLineFeed()
                writeString(string)
                writeString("\'\'\'")
            }
        }
    }

    final override fun writeNullValue() {
        writeString("null")
    }

    // -------- Structure --------

    final override fun startArray() {
        writeChar(StartArray)
    }

    final override fun endArray() {
        writeChar(EndArray)
    }

    final override fun startInlineTable() {
        writeChar(StartInlineTable)
    }

    final override fun endInlineTable() {
        writeChar(EndInlineTable)
    }

    final override fun writeKeyValueSeparator() {
        writeChar(KeyValueSeparator)
    }

    final override fun writeElementSeparator() {
        writeChar(ElementSeparator)
    }

    // -------- Comment --------

    final override fun startComment() {
        writeChar(Comment)
    }

    // -------- Control --------

    final override fun writeSpace() {
        writeChar(' ')
    }

    final override fun writeIndentation(indentation: TomlIndentation) {
        writeString(indentation.representation)
    }

    final override fun writeLineFeed() {
        writeChar('\n')
    }
}
