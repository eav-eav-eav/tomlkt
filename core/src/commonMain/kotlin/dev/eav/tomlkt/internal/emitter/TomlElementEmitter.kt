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

package dev.eav.tomlkt.internal.emitter

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlArray
import dev.eav.tomlkt.TomlBlockArray
import dev.eav.tomlkt.TomlComment
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlInline
import dev.eav.tomlkt.TomlInteger
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlLiteral.Type
import dev.eav.tomlkt.TomlLiteralString
import dev.eav.tomlkt.TomlMultilineString
import dev.eav.tomlkt.TomlNull
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.TomlWriter
import dev.eav.tomlkt.internal.Path
import dev.eav.tomlkt.internal.escape
import dev.eav.tomlkt.internal.processIntegerString
import dev.eav.tomlkt.toBoolean
import dev.eav.tomlkt.toDouble
import dev.eav.tomlkt.toLongOrNull

// -------- AbstractTomlElementEmitter --------

internal abstract class AbstractTomlElementEmitter(
    val toml: Toml,
    val writer: TomlWriter
) {
    var isInline: Boolean = false

    var blockArray: TomlBlockArray? = null

    var isStringMultiline: Boolean = false

    var isStringLiteral: Boolean = false

    var integerRepresentation: TomlInteger? = null

    // -------- Element --------

    fun emitElement(element: TomlElement) {
        when (element) {
            is TomlNull -> {
                emitNull()
            }
            is TomlLiteral -> {
                emitLiteral(element)
            }
            is TomlArray -> {
                createArrayEmitter(element).emitArray(element)
            }
            is TomlTable -> {
                createTableEmitter(element).emitTable(element)
            }
        }
    }

    // -------- Null --------

    open fun emitNull() {
        writer.writeNullValue()
    }

    // -------- Literal --------

    fun emitLiteral(literal: TomlLiteral) {
        when (literal.type) {
            Type.Boolean -> {
                emitBoolean(literal.toBoolean())
            }
            Type.Integer -> {
                val long = literal.toLongOrNull()
                if (long == null) {
                    // This is a ULong.
                    emitULong(literal.content)
                    return
                }
                emitInteger(long)
            }
            Type.Float -> {
                emitFloat(literal.toDouble())
            }
            Type.String -> {
                emitString(literal.toString())
            }
            Type.LocalDateTime, Type.OffsetDateTime, Type.LocalDate, Type.LocalTime -> {
                emitDateTime(literal.content)
            }
        }
    }

    open fun emitBoolean(boolean: Boolean) {
        writer.writeBooleanValue(boolean)
    }

    open fun emitInteger(integer: Long) {
        val representation = integerRepresentation
        if (representation == null) {
            writer.writeIntegerValue(integer)
            return
        }
        writer.writeIntegerValue(
            integer = integer,
            base = representation.base,
            group = representation.group,
            uppercase = toml.config.uppercaseInteger
        )
    }

    // We handle ULong separately because it's not covered in the spec.
    open fun emitULong(content: String) {
        val representation = integerRepresentation
        if (representation == null) {
            writer.writeString(content)
            return
        }
        require(representation.group >= 0) { "Group size cannot be negative" }
        val string = processIntegerString(
            raw = content,
            base = representation.base,
            group = representation.group,
            uppercase = toml.config.uppercaseInteger
        )
        writer.writeString(string)
    }

    open fun emitFloat(float: Double) {
        writer.writeFloatValue(float)
    }

    open fun emitString(string: String) {
        writer.writeStringValue(
            string = string,
            isMultiline = isStringMultiline,
            isLiteral = isStringLiteral
        )
    }

    open fun emitDateTime(content: String) {
        writer.writeString(content)
    }

    // -------- Array --------

    open fun createArrayEmitter(array: TomlArray): AbstractTomlElementEmitter {
        return TomlInlineArrayEmitter(this)
    }

    open fun emitArray(array: TomlArray) {
        createArrayEmitter(array).emitArray(array)
    }

    // -------- Table --------

    open fun createTableEmitter(table: TomlTable): AbstractTomlElementEmitter {
        return TomlInlineTableEmitter(this)
    }

    open fun emitTable(table: TomlTable) {
        createTableEmitter(table).emitTable(table)
    }
}

// -------- TomlElementEmitter --------

// This is the entry point.
internal class TomlElementEmitter(
    toml: Toml,
    writer: TomlWriter
) : AbstractTomlElementEmitter(toml, writer) {
    override fun createArrayEmitter(array: TomlArray): AbstractTomlElementEmitter {
        return if (array.isNotEmpty()) {
            TomlBlockArrayEmitter(this, toml.config.itemsPerLineInBlockArray)
        } else {
            TomlInlineArrayEmitter(this)
        }
    }

    override fun createTableEmitter(table: TomlTable): AbstractTomlElementEmitter {
        return TomlTableEmitter(this, emptyList())
    }
}

// -------- TomlInlineTableEmitter --------

private class TomlInlineTableEmitter(
    delegate: AbstractTomlElementEmitter
) : AbstractTomlElementEmitter(delegate.toml, delegate.writer) {
    override fun emitTable(table: TomlTable) {
        // Begin structure.
        writer.startInlineTable()
        writer.writeSpace()
        // Emit structure.
        val explicitNulls = toml.config.explicitNulls
        val annotations = table.annotations
        val lastIndex = table.size - 1
        table.entries.forEachIndexed loop@{ index, (key, element) ->
            if (element is TomlNull && !explicitNulls) {
                return@loop
            }
            // Begin element.
            val elementAnnotations = annotations[key]
            if (elementAnnotations != null) {
                processAnnotations(elementAnnotations)
            }
            // Emit entry.
            writer.startEntry(key)
            emitElement(element)
            // End element.
            isStringLiteral = false
            integerRepresentation = null
            if (index < lastIndex) {
                writer.writeElementSeparator()
                writer.writeSpace()
            }
        }
        // End structure.
        writer.writeSpace()
        writer.endInlineTable()
    }

    private fun processAnnotations(annotations: List<Annotation>) {
        for (annotation in annotations) {
            when (annotation) {
                is TomlLiteralString -> {
                    isStringLiteral = true
                }
                is TomlInteger -> {
                    integerRepresentation = annotation
                }
            }
        }
    }
}

// -------- TomlInlineArrayEmitter --------

private class TomlInlineArrayEmitter(
    delegate: AbstractTomlElementEmitter
) : AbstractTomlElementEmitter(delegate.toml, delegate.writer) {
    override fun emitArray(array: TomlArray) {
        // Begin structure.
        writer.startArray()
        writer.writeSpace()
        // Emit structure.
        val explicitNulls = toml.config.explicitNulls
        val annotations = array.annotations
        val lastIndex = array.size - 1
        array.forEachIndexed loop@{ index, element ->
            if (element is TomlNull && !explicitNulls) {
                return@loop
            }
            // Begin element.
            val elementAnnotations = annotations.getOrNull(index)
            if (elementAnnotations != null) {
                processAnnotations(elementAnnotations)
            }
            // Emit value.
            emitElement(element)
            // End element.
            isStringLiteral = false
            integerRepresentation = null
            if (index < lastIndex) {
                writer.writeElementSeparator()
                writer.writeSpace()
            }
        }
        // End structure.
        writer.writeSpace()
        writer.endArray()
    }

    private fun processAnnotations(annotations: List<Annotation>) {
        for (annotation in annotations) {
            when (annotation) {
                is TomlLiteralString -> {
                    isStringLiteral = true
                }
                is TomlInteger -> {
                    integerRepresentation = annotation
                }
            }
        }
    }
}

// -------- TomlBlockArrayEmitter --------

private class TomlBlockArrayEmitter(
    delegate: AbstractTomlElementEmitter,
    private val itemsPerLine: Int
) : AbstractTomlElementEmitter(delegate.toml, delegate.writer) {
    override fun emitArray(array: TomlArray) {
        // Begin structure.
        writer.startArray()
        writer.writeLineFeed()
        // Emit structure.
        val explicitNulls = toml.config.explicitNulls
        val indentation = toml.config.indentation
        val itemsPerLine = itemsPerLine
        val annotations = array.annotations
        val lastIndex = array.size - 1
        var currentLineItemCount = 0
        array.forEachIndexed loop@{ index, element ->
            if (element is TomlNull && !explicitNulls) {
                return@loop
            }
            // Begin element.
            val elementAnnotations = annotations.getOrNull(index)
            if (elementAnnotations != null) {
                processAnnotations(elementAnnotations)
            }
            if (currentLineItemCount == 0) {
                // Initial indentation per line.
                writer.writeIndentation(indentation)
            } else {
                writer.writeSpace()
            }
            // Emit value.
            emitElement(element)
            // End element.
            isStringLiteral = false
            integerRepresentation = null
            if (index < lastIndex) {
                writer.writeElementSeparator()
            }
            currentLineItemCount++
            if (currentLineItemCount >= itemsPerLine) {
                writer.writeLineFeed()
                currentLineItemCount = 0
            }
        }
        // End structure.
        if (currentLineItemCount != 0) {
            writer.writeLineFeed()
        }
        writer.endArray()
    }

    private fun processAnnotations(annotations: List<Annotation>) {
        var comment: TomlComment? = null
        for (annotation in annotations) {
            when (annotation) {
                is TomlComment -> {
                    comment = annotation
                }
                is TomlLiteralString -> {
                    isStringLiteral = true
                }
                is TomlInteger -> {
                    integerRepresentation = annotation
                }
            }
        }
        // Emit comment only if there's only one item per line.
        if (comment != null && itemsPerLine == 1) {
            emitComment(comment)
        }
    }

    private fun emitComment(comment: TomlComment) {
        val indentation = toml.config.indentation
        val lines = comment.text.trimIndent().split('\n').map(String::escape)
        for (line in lines) {
            writer.writeIndentation(indentation)
            writer.startComment()
            writer.writeSpace()
            writer.writeString(line)
            writer.writeLineFeed()
        }
    }
}

// -------- TomlTableEmitter --------

private class TomlTableEmitter(
    delegate: AbstractTomlElementEmitter,
    private val path: Path
) : AbstractTomlElementEmitter(delegate.toml, delegate.writer) {
    // We delay the emission of comments.
    private var comment: TomlComment? = null

    // An inline comment is emitted after the value, on the same line.
    private var inlineComment: TomlComment? = null

    // Sub-tables, arrays-of-tables and the comments attached to them are
    // collected during the entry pass and emitted after every normal key-value
    // entry. Each emitter instance emits a single table, so these double as the
    // per-table accumulators. They are allocated lazily on the first insert, so
    // a leaf table with only scalar entries -- the common case -- never creates
    // them, and a non-null map is therefore always non-empty.
    private var tables: MutableMap<String, TomlTable>? = null

    private var arrayOfTables: MutableMap<String, TomlArray>? = null

    private var remainingComments: MutableMap<String, TomlComment>? = null

    // Array of table is handled separately.
    override fun createArrayEmitter(array: TomlArray): AbstractTomlElementEmitter {
        return if (!isInline && array.isNotEmpty()) {
            val itemsPerLine = blockArray?.itemsPerLine ?: toml.config.itemsPerLineInBlockArray
            TomlBlockArrayEmitter(this, itemsPerLine)
        } else {
            TomlInlineArrayEmitter(this)
        }
    }

    // Table is handled separately.
    override fun createTableEmitter(table: TomlTable): AbstractTomlElementEmitter {
        return TomlInlineTableEmitter(this)
    }

    override fun emitTable(table: TomlTable) {
        // Emit structure.
        val explicitNulls = toml.config.explicitNulls
        val annotations = table.annotations
        var hasNormalEntry = false
        // Emit all the normal key-value entries, record tables and array of tables.
        table.entries.forEachIndexed loop@{ _, (key, element) ->
            if (element is TomlNull && !explicitNulls) {
                return@loop
            }
            // Begin element.
            val elementAnnotations = annotations[key]
            if (elementAnnotations != null) {
                processAnnotations(elementAnnotations)
            }
            // Emit entry.
            val emitted = tryEmitEntry(
                key = key,
                element = element,
                hasNormalEntry = hasNormalEntry
            )
            // End element.
            comment = null
            inlineComment = null
            isInline = false
            blockArray = null
            isStringMultiline = false
            isStringLiteral = false
            integerRepresentation = null
            if (!hasNormalEntry) {
                hasNormalEntry = emitted
            }
        }
        // Emit tables. A non-null map was created on the first insert, so it is
        // never empty here.
        val tables = tables
        if (tables != null) {
            if (hasNormalEntry) {
                writer.writeLineFeed()
            }
            emitInnerTables(tables, hasNormalEntry || path.isNotEmpty())
        }
        // Emit array of tables.
        val arrayOfTables = arrayOfTables
        if (arrayOfTables != null) {
            if (hasNormalEntry || tables != null) {
                writer.writeLineFeed()
            }
            emitInnerArrayOfTables(
                arrayOfTables,
                hasNormalEntry || tables != null || path.isNotEmpty()
            )
        }
    }

    private fun processAnnotations(annotations: List<Annotation>) {
        for (annotation in annotations) {
            when (annotation) {
                is TomlComment -> {
                    if (annotation.inline) {
                        inlineComment = annotation
                    } else {
                        comment = annotation
                    }
                }
                is TomlInline -> {
                    isInline = true
                }
                is TomlBlockArray -> {
                    blockArray = annotation
                }
                is TomlMultilineString -> {
                    isStringMultiline = true
                }
                is TomlLiteralString -> {
                    isStringLiteral = true
                }
                is TomlInteger -> {
                    integerRepresentation = annotation
                }
            }
        }
    }

    private fun emitComment(comment: TomlComment) {
        val lines = comment.text.trimIndent().split('\n').map(String::escape)
        for (line in lines) {
            writer.startComment()
            writer.writeSpace()
            writer.writeString(line)
            writer.writeLineFeed()
        }
    }

    private fun tryEmitEntry(
        key: String,
        element: TomlElement,
        hasNormalEntry: Boolean
    ): Boolean {
        val comment = comment
        // Record tables and array of tables.
        when (element) {
            is TomlArray -> {
                val shouldStructure = !isInline &&
                        blockArray == null &&
                        element.isNotEmpty() &&
                        element.all { it is TomlTable } &&
                        element.annotations.flatten().none { it is TomlInline }
                if (shouldStructure) {
                    val arrayOfTables = arrayOfTables
                        ?: mutableMapOf<String, TomlArray>().also { arrayOfTables = it }
                    arrayOfTables[key] = element
                    if (comment != null) {
                        deferComment(key, comment)
                    }
                    return false
                }
            }
            is TomlTable -> {
                val shouldStructure = !isInline && element.isNotEmpty()
                if (shouldStructure) {
                    val tables = tables
                        ?: mutableMapOf<String, TomlTable>().also { tables = it }
                    tables[key] = element
                    if (comment != null) {
                        deferComment(key, comment)
                    }
                    return false
                }
            }
            else -> {}
        }
        // Emit normal key-value entries.
        if (hasNormalEntry) {
            writer.writeLineFeed()
        }
        if (comment != null) {
            emitComment(comment)
        }
        writer.startEntry(key)
        emitElement(element)
        inlineComment?.let { emitInlineComment(it) }
        return true
    }

    private fun emitInlineComment(comment: TomlComment) {
        writer.writeSpace()
        writer.startComment()
        writer.writeSpace()
        writer.writeString(comment.text.escape())
    }

    // Records the comment of a deferred sub-table or array-of-tables, creating
    // the map on first use so a table without such comments never allocates it.
    private fun deferComment(key: String, comment: TomlComment) {
        val remainingComments = remainingComments
            ?: mutableMapOf<String, TomlComment>().also { remainingComments = it }
        remainingComments[key] = comment
    }

    private fun emitInnerTables(
        tables: Map<String, TomlTable>,
        precededByContent: Boolean
    ) {
        val path = path
        val remainingComments = remainingComments
        val lastIndex = tables.size - 1
        tables.entries.forEachIndexed { index, (key, table) ->
            // Begin element: a blank line separates this table from preceding
            // content, then its comment (if any) sits directly above the head.
            // The first head has no separator when it opens the document.
            val innerPath = path + key
            if (index > 0 || precededByContent) {
                writer.writeLineFeed()
            }
            remainingComments?.get(key)?.let { emitComment(it) }
            writer.writeRegularTableHead(innerPath)
            writer.writeLineFeed()
            TomlTableEmitter(this, innerPath).emitTable(table)
            // End element.
            if (index < lastIndex) {
                writer.writeLineFeed()
            }
        }
    }

    private fun emitInnerArrayOfTables(
        arrayOfTables: Map<String, TomlArray>,
        precededByContent: Boolean
    ) {
        val path = path
        val remainingComments = remainingComments
        val lastIndex = arrayOfTables.size - 1
        arrayOfTables.entries.forEachIndexed { index, (key, array) ->
            // Begin element: a blank line separates this block from preceding
            // content, then its comment (if any) sits directly above the head.
            // The first head has no separator when it opens the document.
            val innerPath = path + key
            if (index > 0 || precededByContent) {
                writer.writeLineFeed()
            }
            remainingComments?.get(key)?.let { emitComment(it) }
            TomlArrayOfTableEmitter(this, innerPath, suppressFirstLineFeed = true).emitArray(array)
            // End element.
            if (index < lastIndex) {
                writer.writeLineFeed()
            }
        }
    }
}

// -------- TomlArrayOfTableEmitter --------

private class TomlArrayOfTableEmitter(
    delegate: AbstractTomlElementEmitter,
    private val path: Path,
    private val suppressFirstLineFeed: Boolean = false
) : AbstractTomlElementEmitter(delegate.toml, delegate.writer) {
    override fun createTableEmitter(table: TomlTable): AbstractTomlElementEmitter {
        return TomlTableEmitter(this, path)
    }

    override fun emitArray(array: TomlArray) {
        val path = path
        val annotations = array.annotations
        val lastIndex = array.size - 1
        array.forEachIndexed { index, table ->
            table as TomlTable
            // Begin element.
            val elementAnnotations = annotations.getOrNull(index)
            if (elementAnnotations != null) {
                processAnnotations(elementAnnotations)
            }
            // The first head's separator is written by the caller when it places
            // a comment directly above it, so don't emit a second line feed here.
            if (!(index == 0 && suppressFirstLineFeed)) {
                writer.writeLineFeed()
            }
            writer.writeArrayOfTableHead(path)
            writer.writeLineFeed()
            // Emit value.
            createTableEmitter(table).emitTable(table)
            if (index < lastIndex) {
                writer.writeLineFeed()
            }
        }
    }

    private fun processAnnotations(annotations: List<Annotation>) {
        var comment: TomlComment? = null
        for (annotation in annotations) {
            when (annotation) {
                is TomlComment -> {
                    comment = annotation
                }
            }
        }
        if (comment != null) {
            writer.writeLineFeed()
            emitComment(comment)
        }
    }

    private fun emitComment(comment: TomlComment) {
        val lines = comment.text.trimIndent().split('\n').map(String::escape)
        for (line in lines) {
            writer.startComment()
            writer.writeSpace()
            writer.writeString(line)
            writer.writeLineFeed()
        }
    }
}

// -------- Utils --------

private fun TomlWriter.startEntry(key: String) {
    writeKey(key)
    writeSpace()
    writeKeyValueSeparator()
    writeSpace()
}

private fun TomlWriter.writeRegularTableHead(path: Path) {
    startRegularTableHead()
    writePath(path)
    endRegularTableHead()
}

private fun TomlWriter.writeArrayOfTableHead(path: Path) {
    startArrayOfTableHead()
    writePath(path)
    endArrayOfTableHead()
}

private fun TomlWriter.writePath(path: Path) {
    val lastIndex = path.lastIndex
    for (i in 0..<lastIndex) {
        writeKey(path[i])
        writeKeySeparator()
    }
    writeKey(path[lastIndex])
}
