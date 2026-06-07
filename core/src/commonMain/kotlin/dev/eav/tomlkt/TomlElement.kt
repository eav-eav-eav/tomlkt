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

import dev.eav.tomlkt.TomlLiteral.Type
import dev.eav.tomlkt.internal.NonPrimitiveKeyException
import dev.eav.tomlkt.internal.TomlArraySerializer
import dev.eav.tomlkt.internal.TomlElementSerializer
import dev.eav.tomlkt.internal.TomlLiteralSerializer
import dev.eav.tomlkt.internal.TomlNullSerializer
import dev.eav.tomlkt.internal.TomlTableSerializer
import dev.eav.tomlkt.internal.doubleQuotedIfNotPure
import dev.eav.tomlkt.internal.escape
import dev.eav.tomlkt.internal.parser.ArrayNode
import dev.eav.tomlkt.internal.parser.KeyNode
import dev.eav.tomlkt.internal.parser.TreeNode
import dev.eav.tomlkt.internal.parser.ValueNode
import dev.eav.tomlkt.internal.throwNonPrimitiveKey
import dev.eav.tomlkt.internal.toStringModified
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.contracts.contract

// -------- TomlElement --------

/**
 * Represents anything in TOML, including and only including [TomlNull],
 * [TomlLiteral], [TomlArray], [TomlTable].
 *
 * **NOTE**: only use [Toml] to serialize/deserialize this or any subclass.
 */
@Serializable(with = TomlElementSerializer::class)
public sealed class TomlElement {
    /**
     * The content of this element.
     */
    public abstract val content: Any?

    /**
     * The string representation of this element.
     */
    public abstract override fun toString(): String
}

// -------- TomlNull --------

/**
 * Represents `null`.
 *
 * Note that, currently the encoded value ("null") cannot be modified.
 */
@Serializable(with = TomlNullSerializer::class)
public object TomlNull : TomlElement() {
    override val content: Nothing? get() = null

    override fun toString(): String {
        return "null"
    }
}

// ---- To TomlNull ----

/**
 * Casts this [TomlElement] to [TomlNull].
 *
 * @throws IllegalArgumentException if this is not `TomlNull`.
 */
public fun TomlElement.asTomlNull(): TomlNull {
    contract { returns() implies (this@asTomlNull is TomlNull) }

    return requireNotNull(this as? TomlNull) { failConversion("TomlNull") }
}

// -------- TomlLiteral --------

/**
 * Represents a literal value, which could be boolean, integer, float, string,
 * date-time.
 */
@Serializable(with = TomlLiteralSerializer::class)
public class TomlLiteral internal constructor(
    /**
     * The stringified value.
     */
    override val content: String,
    /**
     * The actual data type this literal derives from.
     */
    public val type: Type
) : TomlElement() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TomlLiteral
        if (type != other.type) return false
        if (content != other.content) return false
        return true
    }

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString(): String {
        return content
    }

    /**
     * The actual data type a [TomlLiteral] derives from.
     */
    public enum class Type {
        /**
         * [Boolean][kotlin.Boolean] type.
         */
        Boolean,

        /**
         * [Byte], [Short], [Int], [Long] type.
         */
        Integer,

        /**
         * [Float][kotlin.Float], [Double] type.
         */
        Float,

        /**
         * [Char], [String][kotlin.String] type.
         */
        String,

        /**
         * [NativeLocalDateTime] type.
         */
        LocalDateTime,

        /**
         * [NativeOffsetDateTime] type.
         */
        OffsetDateTime,

        /**
         * [NativeLocalDate] type.
         */
        LocalDate,

        /**
         * [NativeLocalTime] type.
         */
        LocalTime
    }
}

// ---- To TomlLiteral ----

/**
 * Casts this [TomlElement] to [TomlLiteral].
 *
 * @throws IllegalArgumentException if this is not `TomlLiteral`.
 */
public fun TomlElement.asTomlLiteral(): TomlLiteral {
    contract { returns() implies (this@asTomlLiteral is TomlLiteral) }

    return requireNotNull(this as? TomlLiteral) { failConversion("TomlLiteral") }
}

/**
 * Creates a [TomlLiteral] from [boolean].
 */
public fun TomlLiteral(boolean: Boolean): TomlLiteral {
    return TomlLiteral(boolean.toString(), Type.Boolean)
}

/**
 * Creates a [TomlLiteral] from [byte].
 */
public fun TomlLiteral(byte: Byte): TomlLiteral {
    return TomlLiteral(byte.toLong())
}

/**
 * Creates a [TomlLiteral] from [short].
 */
public fun TomlLiteral(short: Short): TomlLiteral {
    return TomlLiteral(short.toLong())
}

/**
 * Creates a [TomlLiteral] from [int].
 */
public fun TomlLiteral(int: Int): TomlLiteral {
    return TomlLiteral(int.toLong())
}

/**
 * Creates a [TomlLiteral] from [long].
 */
public fun TomlLiteral(long: Long): TomlLiteral {
    return TomlLiteral(long.toString(), Type.Integer)
}

/**
 * Creates a [TomlLiteral] from [uByte].
 */
public fun TomlLiteral(uByte: UByte): TomlLiteral {
    return TomlLiteral(uByte.toString(), Type.Integer)
}

/**
 * Creates a [TomlLiteral] from [uShort].
 */
public fun TomlLiteral(uShort: UShort): TomlLiteral {
    return TomlLiteral(uShort.toString(), Type.Integer)
}

/**
 * Creates a [TomlLiteral] from [uInt].
 */
public fun TomlLiteral(uInt: UInt): TomlLiteral {
    return TomlLiteral(uInt.toString(), Type.Integer)
}

/**
 * Creates a [TomlLiteral] from [uLong].
 */
public fun TomlLiteral(uLong: ULong): TomlLiteral {
    return TomlLiteral(uLong.toString(), Type.Integer)
}

/**
 * Creates a [TomlLiteral] from [float].
 *
 * @see toStringModified
 */
public fun TomlLiteral(float: Float): TomlLiteral {
    return TomlLiteral(float.toStringModified(), Type.Float)
}

/**
 * Creates a [TomlLiteral] from [double].
 *
 * @see toStringModified
 */
public fun TomlLiteral(double: Double): TomlLiteral {
    return TomlLiteral(double.toStringModified(), Type.Float)
}

/**
 * Creates a [TomlLiteral] from [char].
 */
public fun TomlLiteral(char: Char): TomlLiteral {
    return TomlLiteral(char.toString())
}

/**
 * Creates a [TomlLiteral] from [string].
 */
public fun TomlLiteral(string: String): TomlLiteral {
    return TomlLiteral(string, Type.String)
}

/**
 * Creates a [TomlLiteral] from [localDateTime].
 */
public fun TomlLiteral(localDateTime: NativeLocalDateTime): TomlLiteral {
    return TomlLiteral(localDateTime.toString().withSeconds(), Type.LocalDateTime)
}

/**
 * Creates a [TomlLiteral] from [offsetDateTime].
 */
public fun TomlLiteral(offsetDateTime: NativeOffsetDateTime): TomlLiteral {
    return TomlLiteral(offsetDateTime.toString().withSeconds(), Type.OffsetDateTime)
}

/**
 * Creates a [TomlLiteral] from [localDate].
 */
public fun TomlLiteral(localDate: NativeLocalDate): TomlLiteral {
    return TomlLiteral(localDate.toString(), Type.LocalDate)
}

/**
 * Creates a [TomlLiteral] from [localTime].
 */
public fun TomlLiteral(localTime: NativeLocalTime): TomlLiteral {
    return TomlLiteral(localTime.toString().withSeconds(), Type.LocalTime)
}

// The native date-time types drop a ":00" seconds component when it is zero
// (e.g. java.time renders 17:45:00 as "17:45"), but several TOML parsers --
// including the toml-test reference -- require seconds to be present. Insert
// ":00" right after the HH:MM minutes when no seconds follow, leaving any
// fractional seconds, offset or "Z" suffix untouched.
private fun String.withSeconds(): String {
    val firstColon = indexOf(':')
    if (firstColon < 0) {
        return this
    }
    val afterMinute = firstColon + 3
    val hasSeconds = afterMinute < length && this[afterMinute] == ':'
    if (hasSeconds) {
        return this
    }
    return substring(0, afterMinute) + ":00" + substring(afterMinute)
}

/**
 * Creates a [TomlLiteral] from [enum].
 *
 * Note that this function delegates to the overload which consumes [String].
 *
 * @param serializersModule could be ignored, but for contextual serialization,
 * this should be present.
 */
public inline fun <reified E : Enum<E>> TomlLiteral(
    enum: E,
    serializersModule: SerializersModule = EmptySerializersModule()
): TomlLiteral {
    val stringRepresentation = serializersModule.serializer<E>()
        .descriptor
        .getElementName(enum.ordinal)
    return TomlLiteral(stringRepresentation)
}

// ---- From TomlLiteral ----

/**
 * Returns [content][TomlLiteral.content] as [Boolean].
 *
 * @throws IllegalArgumentException if `content` cannot be converted into
 * `Boolean`.
 */
public fun TomlLiteral.toBoolean(): Boolean {
    return requireNotNull(toBooleanOrNull()) { "Cannot convert $this to Boolean" }
}

/**
 * Returns [content][TomlLiteral.content] as [Boolean] only if it is "true" or
 * "false", otherwise `null`.
 */
public fun TomlLiteral.toBooleanOrNull(): Boolean? {
    return when (content) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

/**
 * Returns [content][TomlLiteral.content] as [Byte].
 *
 * @throws NumberFormatException if `content` cannot be converted into `Byte`.
 */
public fun TomlLiteral.toByte(): Byte {
    return content.toByte()
}

/**
 * Returns [content][TomlLiteral.content] as [Byte] only if it can be `Byte`,
 * otherwise `null`.
 */
public fun TomlLiteral.toByteOrNull(): Byte? {
    return content.toByteOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [Short].
 *
 * @throws NumberFormatException if `content` cannot be converted into `Short`.
 */
public fun TomlLiteral.toShort(): Short {
    return content.toShort()
}

/**
 * Returns [content][TomlLiteral.content] as [Short] only if it can be `Short`,
 * otherwise `null`.
 */
public fun TomlLiteral.toShortOrNull(): Short? {
    return content.toShortOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [Int].
 *
 * @throws NumberFormatException if `content` cannot be converted into `Int`.
 */
public fun TomlLiteral.toInt(): Int {
    return content.toInt()
}

/**
 * Returns [content][TomlLiteral.content] as [Int] only if it can be `Int`,
 * otherwise `null`.
 */
public fun TomlLiteral.toIntOrNull(): Int? {
    return content.toIntOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [Long].
 *
 * @throws NumberFormatException if `content` cannot be converted into `Long`.
 */
public fun TomlLiteral.toLong(): Long {
    return content.toLong()
}

/**
 * Returns [content][TomlLiteral.content] as [Long] only if it can be `Long`,
 * otherwise `null`.
 */
public fun TomlLiteral.toLongOrNull(): Long? {
    return content.toLongOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [UByte].
 *
 * @throws NumberFormatException if `content` cannot be converted into `UByte`.
 */
public fun TomlLiteral.toUByte(): UByte {
    return content.toUByte()
}

/**
 * Returns [content][TomlLiteral.content] as [UByte] only if it can be `UByte`,
 * otherwise `null`.
 */
public fun TomlLiteral.toUByteOrNull(): UByte? {
    return content.toUByteOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [UShort].
 *
 * @throws NumberFormatException if `content` cannot be converted into `UShort`.
 */
public fun TomlLiteral.toUShort(): UShort {
    return content.toUShort()
}

/**
 * Returns [content][TomlLiteral.content] as [UShort] only if it can be `UShort`,
 * otherwise `null`.
 */
public fun TomlLiteral.toUShortOrNull(): UShort? {
    return content.toUShortOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [UInt].
 *
 * @throws NumberFormatException if `content` cannot be converted into `UInt`.
 */
public fun TomlLiteral.toUInt(): UInt {
    return content.toUInt()
}

/**
 * Returns [content][TomlLiteral.content] as [UInt] only if it can be `UInt`,
 * otherwise `null`.
 */
public fun TomlLiteral.toUIntOrNull(): UInt? {
    return content.toUIntOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [ULong].
 *
 * @throws NumberFormatException if `content` cannot be converted into `ULong`.
 */
public fun TomlLiteral.toULong(): ULong {
    return content.toULong()
}

/**
 * Returns [content][TomlLiteral.content] as [ULong] only if it can be `ULong`,
 * otherwise `null`.
 */
public fun TomlLiteral.toULongOrNull(): ULong? {
    return content.toULongOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [Float].
 *
 * @throws NumberFormatException if `content` cannot be converted into `Float`.
 */
public fun TomlLiteral.toFloat(): Float {
    return toFloatOrNull() ?: throw NumberFormatException("Cannot convert $this to Float")
}

/**
 * Returns [content][TomlLiteral.content] as [Float] only if it can be an exact
 * `Float` or `inf`/`+inf`/`-inf`/`nan`, otherwise `null`.
 */
public fun TomlLiteral.toFloatOrNull(): Float? {
    return when (content) {
        "inf" -> Float.POSITIVE_INFINITY
        "+inf" -> Float.POSITIVE_INFINITY
        "-inf" -> Float.NEGATIVE_INFINITY
        "nan" -> Float.NaN
        else -> content.toFloatOrNull()
    }
}

/**
 * Returns [content][TomlLiteral.content] as [Double].
 *
 * @throws NumberFormatException if `content` cannot be converted into `Double`.
 */
public fun TomlLiteral.toDouble(): Double {
    return toDoubleOrNull() ?: throw NumberFormatException("Cannot convert $this to Double")
}

/**
 * Returns [content][TomlLiteral.content] as [Double] only if it can be an exact
 * `Double` or `inf`/`+inf`/`-inf`/`nan`, otherwise `null`.
 */
public fun TomlLiteral.toDoubleOrNull(): Double? {
    return when (content) {
        "inf" -> Double.POSITIVE_INFINITY
        "+inf" -> Double.POSITIVE_INFINITY
        "-inf" -> Double.NEGATIVE_INFINITY
        "nan" -> Double.NaN
        else -> content.toDoubleOrNull()
    }
}

/**
 * Returns [content][TomlLiteral.content] as [Char].
 *
 * @throws NoSuchElementException if `content` is empty.
 * @throws IllegalArgumentException if `content` cannot be converted into `Char`.
 */
public fun TomlLiteral.toChar(): Char {
    return content.single()
}

/**
 * Returns [content][TomlLiteral.content] as [Char] only if the length is
 * exactly 1, otherwise `null`.
 */
public fun TomlLiteral.toCharOrNull(): Char? {
    return content.singleOrNull()
}

/**
 * Returns [content][TomlLiteral.content] as [TomlLocalDateTime].
 *
 * @throws IllegalArgumentException if `content` cannot be parsed into
 * `TomlLocalDateTime`.
 */
public fun TomlLiteral.toLocalDateTime(): TomlLocalDateTime {
    return NativeLocalDateTime(content)
}

/**
 * Returns [content][TomlLiteral.content] as [TomlLocalDateTime] only if it can
 * be parsed, otherwise `null`.
 */
public fun TomlLiteral.toLocalDateTimeOrNull(): TomlLocalDateTime? {
    return try {
        toLocalDateTime()
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Returns [content][TomlLiteral.content] as [TomlOffsetDateTime].
 *
 * @throws IllegalArgumentException if `content` cannot be parsed into
 * `TomlOffsetDateTime`.
 */
public fun TomlLiteral.toOffsetDateTime(): TomlOffsetDateTime {
    return NativeOffsetDateTime(content)
}

/**
 * Returns [content][TomlLiteral.content] as [TomlOffsetDateTime] only if it can
 * be parsed, otherwise `null`.
 */
public fun TomlLiteral.toOffsetDateTimeOrNull(): TomlOffsetDateTime? {
    return try {
        toOffsetDateTime()
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Returns [content][TomlLiteral.content] as [TomlLocalDate].
 *
 * @throws IllegalArgumentException if `content` cannot be parsed into
 * `TomlLocalDate`.
 */
public fun TomlLiteral.toLocalDate(): TomlLocalDate {
    return NativeLocalDate(content)
}

/**
 * Returns [content][TomlLiteral.content] as [TomlLocalDate] only if it can be
 * parsed, otherwise `null`.
 */
public fun TomlLiteral.toLocalDateOrNull(): TomlLocalDate? {
    return try {
        toLocalDate()
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Returns [content][TomlLiteral.content] as [TomlLocalTime].
 *
 * @throws IllegalArgumentException if `content` cannot be parsed into
 * `TomlLocalTime`.
 */
public fun TomlLiteral.toLocalTime(): TomlLocalTime {
    return NativeLocalTime(content)
}

/**
 * Returns [content][TomlLiteral.content] as [TomlLocalTime] only if it can be
 * parsed, otherwise `null`.
 */
public fun TomlLiteral.toLocalTimeOrNull(): TomlLocalTime? {
    return try {
        toLocalTime()
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Returns [content][TomlLiteral.content] as an enum value of the corresponding
 * [type][E].
 *
 * @param serializersModule could be ignored, but for contextual serialization,
 * this should be present.
 *
 * @throws IllegalArgumentException if `content` cannot be converted into `E`.
 */
public inline fun <reified E : Enum<E>> TomlLiteral.toEnum(
    serializersModule: SerializersModule = EmptySerializersModule()
): E {
    return requireNotNull(toEnumOrNull<E>(serializersModule)) {
        "Cannot convert $this to ${E::class.simpleName}"
    }
}

/**
 * Returns [content][TomlLiteral.content] as an enum value of the corresponding
 * [type][E] only if it suits in, otherwise `null`.
 *
 * @param serializersModule could be ignored, but for contextual serialization,
 * this should be present.
 */
public inline fun <reified E : Enum<E>> TomlLiteral.toEnumOrNull(
    serializersModule: SerializersModule = EmptySerializersModule()
): E? {
    val index = serializersModule.serializer<E>()
        .descriptor
        .elementNames
        .indexOf(content)
    return if (index != -1) {
        enumValues<E>()[index]
    } else {
        null
    }
}

// -------- TomlArray --------

/**
 * Represents array in TOML, whose values are [TomlElement].
 *
 * This type delegates everything to [content], thus all the functions from
 * [List] could be used.
 */
@Serializable(with = TomlArraySerializer::class)
public class TomlArray internal constructor(
    override val content: List<TomlElement>,
    /**
     * The metadata attached to each value.
     */
    public val annotations: List<List<Annotation>> = emptyList()
) : TomlElement(), List<TomlElement> by content {
    override fun equals(other: Any?): Boolean {
        return content == other
    }

    override fun hashCode(): Int {
        return content.hashCode()
    }

    override fun toString(): String {
        return content.joinToString(prefix = "[ ", postfix = " ]")
    }

    @Suppress("UndocumentedPublicClass")
    public companion object {
        /**
         * An empty [TomlArray].
         */
        public val Empty: TomlArray = TomlArray(emptyList())
    }
}

// ---- To TomlArray ----

/**
 * Casts this [TomlElement] to [TomlArray].
 *
 * @throws IllegalArgumentException if this is not `TomlArray`.
 */
public fun TomlElement.asTomlArray(): TomlArray {
    contract { returns() implies (this@asTomlArray is TomlArray) }

    return requireNotNull(this as? TomlArray) { failConversion("TomlArray") }
}

/**
 * Creates a [TomlArray] from [iterable].
 */
public fun TomlArray(iterable: Iterable<*>): TomlArray {
    val content = iterable.map { it.toTomlElement() }
    return TomlArray(content)
}

/**
 * Creates a [TomlArray] with [values].
 */
public fun TomlArray(vararg values: Any?): TomlArray {
    val content = values.map { it.toTomlElement() }
    return TomlArray(content)
}

// ---- Extensions for TomlLiteral ----

/**
 * Annotates this [TomlArray] with [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlArray.annotated(annotations: List<List<Annotation>>): TomlArray {
    return TomlArray(content, annotations)
}

/**
 * Annotates this [TomlArray] with [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlArray.annotated(vararg annotations: List<Annotation>): TomlArray {
    return TomlArray(content, annotations.asList())
}

/**
 * Annotates all the values from this [TomlArray] with the same [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlArray.allAnnotated(annotations: List<Annotation>): TomlArray {
    val converted = List(size) { annotations }
    return TomlArray(content, converted)
}

/**
 * Annotates all the values from this [TomlArray] with the same [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlArray.allAnnotated(vararg annotations: Annotation): TomlArray {
    val annotationsAsList = annotations.asList()
    val annotationsList = List(size) { annotationsAsList }
    return TomlArray(content, annotationsList)
}

/**
 * Gets a [TomlLiteral] with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a `TomlLiteral`.
 */
public fun TomlArray.getLiteral(index: Int): TomlLiteral {
    return checkNotNull(getLiteralOrNull(index)) { "Cannot find a TomlLiteral with index $index" }
}

/**
 * Gets a [TomlLiteral] with [index] from this [TomlArray]. If the element does
 * not exist, or it is not a `TomlLiteral`, this returns `null`.
 */
public fun TomlArray.getLiteralOrNull(index: Int): TomlLiteral? {
    return getOrNull(index) as? TomlLiteral
}

/**
 * Gets a boolean with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a boolean.
 */
public fun TomlArray.getBoolean(index: Int): Boolean {
    return checkNotNull(getBooleanOrNull(index)) { "Cannot find a boolean with index $index" }
}

/**
 * Gets a boolean with [index] from this [TomlArray]. If the element does not
 * exist, or it is not a [TomlLiteral], or it cannot be converted to a boolean,
 * this returns `null`.
 */
public fun TomlArray.getBooleanOrNull(index: Int): Boolean? {
    return getLiteralOrNull(index)?.toBooleanOrNull()
}

/**
 * Gets an integer (TOML integer, which is [Long]) with [index] from this
 * [TomlArray].
 *
 * **NOTE**: if the original value comes from a [ULong], this function also
 * throws [IllegalStateException], because a TOML integer is actually a
 * **signed** `Long`, but `ULong` exceeds the range of it. Please use
 * [TomlLiteral.toULongOrNull] instead.
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to an integer.
 */
public fun TomlArray.getInteger(index: Int): Long {
    return checkNotNull(getIntegerOrNull(index)) { "Cannot find an integer with index $index" }
}

/**
 * Gets an integer (TOML integer, which is [Long]) with [index] from this
 * [TomlArray]. If the element does not exist, or it is not a [TomlLiteral], or
 * it cannot be converted to an integer, this returns `null`.
 *
 * **NOTE**: if the original value comes from a [ULong], this function also
 * returns `null`, because a TOML integer is actually a **signed** `Long`, but
 * `ULong` exceeds the range of it. Please use [TomlLiteral.toULongOrNull]
 * instead.
 */
public fun TomlArray.getIntegerOrNull(index: Int): Long? {
    return getLiteralOrNull(index)?.toLongOrNull()
}

/**
 * Gets a float (TOML float, which is [Double]) with [index] from this
 * [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a float.
 */
public fun TomlArray.getFloat(index: Int): Double {
    return checkNotNull(getFloatOrNull(index)) { "Cannot find a float with index $index" }
}

/**
 * Gets a float (TOML float, which is [Double]) with [index] from this
 * [TomlArray]. If the element does not exist, or it is not a [TomlLiteral], or
 * it cannot be converted to a float, this returns `null`.
 */
public fun TomlArray.getFloatOrNull(index: Int): Double? {
    return getLiteralOrNull(index)?.toDoubleOrNull()
}

/**
 * Gets a string with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a string.
 */
public fun TomlArray.getString(index: Int): String {
    return checkNotNull(getStringOrNull(index)) { "Cannot find a string with index $index" }
}

/**
 * Gets a string with [index] from this [TomlArray]. If the element does not
 * exist, or it is not a [TomlLiteral], or it cannot be converted to a string,
 * this returns `null`.
 */
public fun TomlArray.getStringOrNull(index: Int): String? {
    return getLiteralOrNull(index)?.toString()
}

/**
 * Gets a [TomlLocalDateTime] with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a `TomlLocalDateTime`.
 */
public fun TomlArray.getLocalDateTime(index: Int): TomlLocalDateTime {
    return checkNotNull(getLocalDateTimeOrNull(index)) {
        "Cannot find a TomlLocalDateTime with index $index"
    }
}

/**
 * Gets a [TomlLocalDateTime] with [index] from this [TomlArray]. If the element
 * does not exist, or it is not a [TomlLiteral], or it cannot be converted to a
 * `TomlLocalDateTime`, this returns `null`.
 */
public fun TomlArray.getLocalDateTimeOrNull(index: Int): TomlLocalDateTime? {
    return getLiteralOrNull(index)?.toLocalDateTimeOrNull()
}

/**
 * Gets a [TomlOffsetDateTime] with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a `TomlOffsetDateTime`.
 */
public fun TomlArray.getOffsetDateTime(index: Int): TomlOffsetDateTime {
    return checkNotNull(getOffsetDateTimeOrNull(index)) {
        "Cannot find a TomlOffsetDateTime with index $index"
    }
}

/**
 * Gets a [TomlOffsetDateTime] with [index] from this [TomlArray]. If the
 * element does not exist, or it is not a [TomlLiteral], or it cannot be
 * converted to a `TomlOffsetDateTime`, this returns `null`.
 */
public fun TomlArray.getOffsetDateTimeOrNull(index: Int): TomlOffsetDateTime? {
    return getLiteralOrNull(index)?.toOffsetDateTimeOrNull()
}

/**
 * Gets a [TomlLocalDate] with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a `TomlLocalDate`.
 */
public fun TomlArray.getLocalDate(index: Int): TomlLocalDate {
    return checkNotNull(getLocalDateOrNull(index)) {
        "Cannot find a TomlLocalDate with index $index"
    }
}

/**
 * Gets a [TomlLocalDate] with [index] from this [TomlArray]. If the element
 * does not exist, or it is not a [TomlLiteral], or it cannot be converted to a
 * `TomlLocalDate`, this returns `null`.
 */
public fun TomlArray.getLocalDateOrNull(index: Int): TomlLocalDate? {
    return getLiteralOrNull(index)?.toLocalDateOrNull()
}

/**
 * Gets a [TomlLocalTime] with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a `TomlLocalTime`.
 */
public fun TomlArray.getLocalTime(index: Int): TomlLocalTime {
    return checkNotNull(getLocalTimeOrNull(index)) {
        "Cannot find a TomlLocalTime with index $index"
    }
}

/**
 * Gets a [TomlLocalTime] with [index] from this [TomlArray]. If the element
 * does not exist, or it is not a [TomlLiteral], or it cannot be converted to a
 * `TomlLocalTime`, this returns `null`.
 */
public fun TomlArray.getLocalTimeOrNull(index: Int): TomlLocalTime? {
    return getLiteralOrNull(index)?.toLocalTimeOrNull()
}

/**
 * Gets a [TomlArray] with [index] from this `TomlArray`.
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a `TomlArray`.
 */
public fun TomlArray.getArray(index: Int): TomlArray {
    return checkNotNull(getArrayOrNull(index)) { "Cannot find a TomlArray with index $index" }
}

/**
 * Gets a [TomlArray] with [index] from this `TomlArray`. If the element does
 * not exist, or it is not a `TomlArray`, this returns `null`.
 */
public fun TomlArray.getArrayOrNull(index: Int): TomlArray? {
    return getOrNull(index) as? TomlArray
}

/**
 * Gets a [TomlTable] with [index] from this [TomlArray].
 *
 * @throws IllegalStateException if the corresponding element does not conform
 * to a `TomlTable`.
 */
public fun TomlArray.getTable(index: Int): TomlTable {
    return checkNotNull(getTableOrNull(index)) { "Cannot find a TomlTable with index $index" }
}

/**
 * Gets a [TomlTable] with [index] from this [TomlArray]. If the element does
 * not exist, or it is not a `TomlTable`, this returns `null`.
 */
public fun TomlArray.getTableOrNull(index: Int): TomlTable? {
    return getOrNull(index) as? TomlTable
}

// -------- TomlTable --------

/**
 * Represents table in TOML, whose keys are strings and values are
 * [TomlElement]s.
 *
 * This type delegates everything to [content], thus all the functions from
 * [Map] could be used.
 */
@Serializable(with = TomlTableSerializer::class)
public class TomlTable internal constructor(
    override val content: Map<String, TomlElement>,
    /**
     * The metadata attached to each entry.
     */
    public val annotations: Map<String, List<Annotation>> = emptyMap()
) : TomlElement(), Map<String, TomlElement> by content {
    /**
     * More convenient means than [Map.get] if this table is originally a map
     * with **primitive** keys.
     *
     * @throws NonPrimitiveKeyException if provided non-primitive key.
     */
    public operator fun get(key: Any?): TomlElement? {
        return get(key.toTomlKey())
    }

    override fun equals(other: Any?): Boolean {
        return content == other
    }

    override fun hashCode(): Int {
        return content.hashCode()
    }

    override fun toString(): String {
        return content.entries.joinToString(
            prefix = "{ ",
            postfix = " }"
        ) { (key, value) ->
            val convertedKey = key.escape().doubleQuotedIfNotPure()
            "$convertedKey = $value"
        }
    }

    @Suppress("UndocumentedPublicClass")
    public companion object {
        /**
         * An empty [TomlTable].
         */
        public val Empty: TomlTable = TomlTable(emptyMap())
    }
}

// ---- To TomlTable ----

/**
 * Casts this [TomlElement] to [TomlTable].
 *
 * @throws IllegalArgumentException if this is not `TomlTable`.
 */
public fun TomlElement.asTomlTable(): TomlTable {
    contract { returns() implies (this@asTomlTable is TomlTable) }

    return requireNotNull(this as? TomlTable) { failConversion("TomlTable") }
}

/**
 * Creates a [TomlTable] from [map].
 */
public fun TomlTable(map: Map<*, *>): TomlTable {
    val content = buildMap(map.size) {
        for ((k, v) in map) {
            put(k.toTomlKey(), v.toTomlElement())
        }
    }
    return TomlTable(content)
}

/**
 * Creates a [TomlTable] with [entries].
 */
public fun TomlTable(vararg entries: Pair<*, *>): TomlTable {
    val content = buildMap(entries.size) {
        for ((k, v) in entries) {
            put(k.toTomlKey(), v.toTomlElement())
        }
    }
    return TomlTable(content)
}

// ---- Extensions for TomlTable ----

/**
 * Annotates this [TomlTable] with [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlTable.annotated(annotations: Map<*, List<Annotation>>): TomlTable {
    val converted = buildMap(size) {
        for ((k, v) in annotations) {
            put(k.toTomlKey(), v)
        }
    }
    return TomlTable(content, converted)
}

/**
 * Annotates this [TomlTable] with [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlTable.annotated(vararg annotations: Pair<*, List<Annotation>>): TomlTable {
    val converted = buildMap(annotations.size) {
        for ((k, v) in annotations) {
            put(k.toTomlKey(), v)
        }
    }
    return TomlTable(content, converted)
}

/**
 * Annotates all the entries from this [TomlTable] with the same [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlTable.allAnnotated(annotations: List<Annotation>): TomlTable {
    val annotationsMap = content.keys.associateWith { annotations }
    return TomlTable(content, annotationsMap)
}

/**
 * Annotates all the entries from this [TomlTable] with the same [annotations].
 *
 * **NOTE**: this returns a new instance.
 */
public fun TomlTable.allAnnotated(vararg annotations: Annotation): TomlTable {
    val annotationsAsList = annotations.asList()
    val annotationsMap = content.keys.associateWith { annotationsAsList }
    return TomlTable(content, annotationsMap)
}

/**
 * Gets the element along the path constructed by [keys].
 *
 * Given the following TOML file:
 *
 * ```toml
 * i = 0
 *
 * [data]
 * list = [
 *     "any",
 *     { type: "integer", value: 0 }
 * ]
 * ```
 *
 * With `keys = ["data", "list", 1, "value"]`, the above table emits 0 (in
 * [TomlLiteral]).
 *
 * @param keys the path which leads to the value. Each one item is a single
 * segment. If a [TomlArray] is met, any direct child segment must be [Int] or
 * [String] (will be parsed into integer).
 *
 * @throws NonPrimitiveKeyException if provided non-primitive keys.
 */
public operator fun TomlTable.get(vararg keys: Any?): TomlElement? {
    if (keys.isEmpty()) {
        return this
    }
    return getByPathRecursively(keys, 0)
}

/**
 * Gets a [TomlLiteral] with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a `TomlLiteral`.
 */
public fun TomlTable.getLiteral(key: Any?): TomlLiteral {
    return checkNotNull(getLiteralOrNull(key)) { "Cannot find a TomlLiteral with key $key" }
}

/**
 * Gets a [TomlLiteral] with [key] from this [TomlTable]. If the entry does not
 * exist, or it is not a `TomlLiteral`, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getLiteralOrNull(key: Any?): TomlLiteral? {
    return get(key) as? TomlLiteral
}

/**
 * Gets a boolean with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a boolean.
 */
public fun TomlTable.getBoolean(key: Any?): Boolean {
    return checkNotNull(getBooleanOrNull(key)) { "Cannot find a boolean with key $key" }
}

/**
 * Gets a boolean with [key] from this [TomlTable]. If the entry does not exist,
 * or it is not a [TomlLiteral], or it cannot be converted to a boolean, this
 * returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getBooleanOrNull(key: Any?): Boolean? {
    return getLiteralOrNull(key)?.toBooleanOrNull()
}

/**
 * Gets an integer (TOML integer, which is [Long]) with [key] from this
 * [TomlTable].
 *
 * **NOTE**: if the original value comes from a [ULong], this function also
 * throws [IllegalStateException], because a TOML integer is actually a
 * **signed** `Long`, but `ULong` exceeds the range of it. Please use
 * [TomlLiteral.toULongOrNull] instead.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * an integer.
 */
public fun TomlTable.getInteger(key: Any?): Long {
    return checkNotNull(getIntegerOrNull(key)) { "Cannot find an integer with key $key" }
}

/**
 * Gets an integer (TOML integer, which is [Long]) with [key] from this
 * [TomlTable]. If the entry does not exist, or it is not a [TomlLiteral], or it
 * cannot be converted to an integer, this returns `null`.
 *
 * **NOTE**: if the original value comes from a [ULong], this function also
 * returns `null`, because a TOML integer is actually a **signed** `Long`, but
 * `ULong` exceeds the range of it. Please use [TomlLiteral.toULongOrNull]
 * instead.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getIntegerOrNull(key: Any?): Long? {
    return getLiteralOrNull(key)?.toLongOrNull()
}

/**
 * Gets a float (TOML float, which is [Double]) with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a float.
 */
public fun TomlTable.getFloat(key: Any?): Double {
    return checkNotNull(getFloatOrNull(key)) { "Cannot find a float with key $key" }
}

/**
 * Gets a float (TOML float, which is [Double]) with [key] from this [TomlTable].
 * If the entry does not exist, or it is not a [TomlLiteral], or it cannot be
 * converted to a float, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getFloatOrNull(key: Any?): Double? {
    return getLiteralOrNull(key)?.toDoubleOrNull()
}

/**
 * Gets a string with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a string.
 */
public fun TomlTable.getString(key: Any?): String {
    return checkNotNull(getStringOrNull(key)) { "Cannot find a string with key $key" }
}

/**
 * Gets a string with [key] from this [TomlTable]. If the entry does not exist,
 * or it is not a [TomlLiteral], or it cannot be converted to a string, this
 * returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getStringOrNull(key: Any?): String? {
    return getLiteralOrNull(key)?.toString()
}

/**
 * Gets a [TomlLocalDateTime] with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a `TomlLocalDateTime`.
 */
public fun TomlTable.getLocalDateTime(key: Any?): TomlLocalDateTime {
    return checkNotNull(getLocalDateTimeOrNull(key)) {
        "Cannot find a TomlLocalDateTime with key $key"
    }
}

/**
 * Gets a [TomlLocalDateTime] with [key] from this [TomlTable]. If the entry
 * does not exist, or it is not a [TomlLiteral], or it cannot be converted to a
 * `TomlLocalDateTime`, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getLocalDateTimeOrNull(key: Any?): TomlLocalDateTime? {
    return getLiteralOrNull(key)?.toLocalDateTimeOrNull()
}

/**
 * Gets a [TomlOffsetDateTime] with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a `TomlOffsetDateTime`.
 */
public fun TomlTable.getOffsetDateTime(key: Any?): TomlOffsetDateTime {
    return checkNotNull(getOffsetDateTimeOrNull(key)) {
        "Cannot find a TomlOffsetDateTime with key $key"
    }
}

/**
 * Gets a [TomlOffsetDateTime] with [key] from this [TomlTable]. If the entry
 * does not exist, or it is not a [TomlLiteral], or it cannot be converted to a
 * `TomlOffsetDateTime`, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getOffsetDateTimeOrNull(key: Any?): TomlOffsetDateTime? {
    return getLiteralOrNull(key)?.toOffsetDateTimeOrNull()
}

/**
 * Gets a [TomlLocalDate] with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a `TomlLocalDate`.
 */
public fun TomlTable.getLocalDate(key: Any?): TomlLocalDate {
    return checkNotNull(getLocalDateOrNull(key)) {
        "Cannot find a TomlLocalDate with key $key"
    }
}

/**
 * Gets a [TomlLocalDate] with [key] from this [TomlTable]. If the entry does
 * not exist, or it is not a [TomlLiteral], or it cannot be converted to a
 * `TomlLocalDate`, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getLocalDateOrNull(key: Any?): TomlLocalDate? {
    return getLiteralOrNull(key)?.toLocalDateOrNull()
}

/**
 * Gets a [TomlLocalTime] with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a `TomlLocalTime`.
 */
public fun TomlTable.getLocalTime(key: Any?): TomlLocalTime {
    return checkNotNull(getLocalTimeOrNull(key)) {
        "Cannot find a TomlLocalTime with key $key"
    }
}

/**
 * Gets a [TomlLocalTime] with [key] from this [TomlTable]. If the entry does
 * not exist, or it is not a [TomlLiteral], or it cannot be converted to a
 * `TomlLocalTime`, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getLocalTimeOrNull(key: Any?): TomlLocalTime? {
    return getLiteralOrNull(key)?.toLocalTimeOrNull()
}

/**
 * Gets a [TomlArray] with [key] from this [TomlTable].
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a `TomlArray`.
 */
public fun TomlTable.getArray(key: Any?): TomlArray {
    return checkNotNull(getArrayOrNull(key)) { "Cannot find a TomlArray with key $key" }
}

/**
 * Gets a [TomlArray] with [key] from this [TomlTable]. If the entry does not
 * exist, or it is not a `TomlArray`, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getArrayOrNull(key: Any?): TomlArray? {
    return get(key) as? TomlArray
}

/**
 * Gets a [TomlTable] with [key] from this `TomlTable`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 * @throws IllegalStateException if the corresponding entry does not conform to
 * a `TomlTable`.
 */
public fun TomlTable.getTable(key: Any?): TomlTable {
    return checkNotNull(getTableOrNull(key)) { "Cannot find a TomlTable with key $key" }
}

/**
 * Gets a [TomlTable] with [key] from this `TomlTable`. If the entry does not
 * exist, or it is not a `TomlTable`, this returns `null`.
 *
 * @throws NonPrimitiveKeyException if provided non-primitive key.
 */
public fun TomlTable.getTableOrNull(key: Any?): TomlTable? {
    return get(key) as? TomlTable
}

// ======== Internal ========

internal fun TomlArray(arrayNode: ArrayNode): TomlArray {
    val children = arrayNode.children
    return when (children.size) {
        0 -> TomlArray.Empty
        1 -> {
            val node = children[0]
            val content = listOf(element = node.toTomlElement())
            TomlArray(content, arrayNode.annotations)
        }
        else -> {
            val content = children.map(::TomlTable)
            TomlArray(content, arrayNode.annotations)
        }
    }
}

internal fun TomlTable(keyNode: KeyNode): TomlTable {
    val children = keyNode.children
    return when (val size = children.size) {
        0 -> TomlTable.Empty
        1 -> {
            val (k, n) = children.entries.first()
            val content = mapOf(pair = k to n.toTomlElement())
            TomlTable(content, keyNode.annotations)
        }
        else -> {
            val content = buildMap(size) {
                for ((k, n) in children) {
                    put(k, n.toTomlElement())
                }
            }
            return TomlTable(content, keyNode.annotations)
        }
    }
}

private fun TreeNode.toTomlElement(): TomlElement {
    return when (this) {
        is KeyNode -> TomlTable(this)
        is ArrayNode -> TomlArray(this)
        is ValueNode -> element
    }
}

private tailrec fun TomlTable.getByPathRecursively(
    keys: Array<out Any?>,
    depth: Int
): TomlElement? {
    val key = keys[depth]
    val value = get(key)
    return when {
        depth == keys.lastIndex -> value
        value is TomlTable -> value.getByPathRecursively(keys, depth + 1)
        value is TomlArray -> value.getByPathRecursively(keys, depth + 1)
        else -> null
    }
}

private tailrec fun TomlArray.getByPathRecursively(
    keys: Array<out Any?>,
    depth: Int
): TomlElement? {
    val index = when (val key = keys[depth]) {
        is Int -> key
        is String -> key.toInt()
        else -> {
            val message = "Expect integer key when accessing TomlArray, but found $key"
            throw IllegalArgumentException(message)
        }
    }
    val value = get(index)
    return when {
        depth == keys.lastIndex -> value
        value is TomlTable -> value.getByPathRecursively(keys, depth + 1)
        value is TomlArray -> value.getByPathRecursively(keys, depth + 1)
        else -> null
    }
}

internal fun Any?.toTomlKey(): String {
    return when (this) {
        is Boolean, is Number, is UByte, is UShort, is UInt, is ULong, is Char -> toString()
        is String -> this
        else -> throwNonPrimitiveKey(this)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun Any?.toTomlElement(): TomlElement {
    return when (this) {
        null -> TomlNull
        is TomlElement -> this
        is Boolean -> TomlLiteral(this)
        is Byte -> TomlLiteral(this)
        is Short -> TomlLiteral(this)
        is Int -> TomlLiteral(this)
        is Long -> TomlLiteral(this)
        is UByte -> TomlLiteral(this)
        is UShort -> TomlLiteral(this)
        is UInt -> TomlLiteral(this)
        is ULong -> TomlLiteral(this)
        is Float -> TomlLiteral(this)
        is Double -> TomlLiteral(this)
        is Char -> TomlLiteral(this)
        is String -> TomlLiteral(this)
        is NativeLocalDateTime -> TomlLiteral(this)
        is NativeOffsetDateTime -> TomlLiteral(this)
        is NativeLocalDate -> TomlLiteral(this)
        is NativeLocalTime -> TomlLiteral(this)
        is BooleanArray -> TomlArray(this.asIterable())
        is ByteArray -> TomlArray(this.asIterable())
        is ShortArray -> TomlArray(this.asIterable())
        is IntArray -> TomlArray(this.asIterable())
        is LongArray -> TomlArray(this.asIterable())
        is UByteArray -> TomlArray(this)
        is UShortArray -> TomlArray(this)
        is UIntArray -> TomlArray(this)
        is ULongArray -> TomlArray(this)
        is FloatArray -> TomlArray(this.asIterable())
        is DoubleArray -> TomlArray(this.asIterable())
        is CharArray -> TomlArray(this.asIterable())
        is Array<*> -> TomlArray(this.asIterable())
        is Iterable<*> -> TomlArray(this)
        is Map<*, *> -> TomlTable(this)
        else -> error("Unsupported class: ${this::class.simpleName}")
    }
}

private fun TomlElement.failConversion(target: String): String {
    return "Cannot convert ${this::class.simpleName} to $target"
}
