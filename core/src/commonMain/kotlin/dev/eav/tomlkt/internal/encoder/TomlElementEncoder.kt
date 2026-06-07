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

package dev.eav.tomlkt.internal.encoder

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlArray
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlNull
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.internal.isTomlElement
import dev.eav.tomlkt.internal.isUnsignedInteger
import dev.eav.tomlkt.internal.throwPolymorphicCollection
import dev.eav.tomlkt.internal.throwUnsupportedSerialKind
import dev.eav.tomlkt.toTomlKey
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind.CLASS
import kotlinx.serialization.descriptors.StructureKind.LIST
import kotlinx.serialization.descriptors.StructureKind.MAP
import kotlinx.serialization.descriptors.StructureKind.OBJECT
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder

// -------- AbstractTomlElementEncoder --------

internal abstract class AbstractTomlElementEncoder(toml: Toml) : AbstractTomlEncoder(toml) {
    lateinit var element: TomlElement

    final override fun encodeBoolean(value: Boolean) {
        element = TomlLiteral(value)
    }

    final override fun encodeByte(value: Byte) {
        element = TomlLiteral(value)
    }

    final override fun encodeShort(value: Short) {
        element = TomlLiteral(value)
    }

    final override fun encodeInt(value: Int) {
        element = TomlLiteral(value)
    }

    final override fun encodeLong(value: Long) {
        element = TomlLiteral(value)
    }

    final override fun encodeFloat(value: Float) {
        element = TomlLiteral(value)
    }

    final override fun encodeDouble(value: Double) {
        element = TomlLiteral(value)
    }

    final override fun encodeChar(value: Char) {
        element = TomlLiteral(value)
    }

    final override fun encodeString(value: String) {
        element = TomlLiteral(value)
    }

    final override fun encodeNull() {
        element = TomlNull
    }

    final override fun encodeTomlElement(value: TomlElement) {
        element = value
    }

    final override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        return if (descriptor.isUnsignedInteger) {
            TomlElementInlineEncoder(this)
        } else {
            this
        }
    }

    final override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        encodeString(enumDescriptor.getElementName(index))
    }

    final override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return beginStructurePolymorphically(descriptor)
    }

    final override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        return beginCollectionPolymorphically(descriptor, collectionSize)
    }

    final override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        if (serializer.isTomlElement.not()) {
            super.encodeSerializableValue(serializer, value)
        } else {
            encodeTomlElement(value as TomlElement)
        }
    }
}

// -------- TomlElementEncoder --------

internal class TomlElementEncoder(toml: Toml) : AbstractTomlElementEncoder(toml)

// -------- TomlElementInlineEncoder --------

private class TomlElementInlineEncoder(
    delegate: AbstractTomlElementEncoder
) : AbstractTomlInlineEncoder<AbstractTomlElementEncoder>(delegate) {
    override fun encodeByte(value: Byte) {
        delegate.element = TomlLiteral(value.toUByte())
    }

    override fun encodeShort(value: Short) {
        delegate.element = TomlLiteral(value.toUShort())
    }

    override fun encodeInt(value: Int) {
        delegate.element = TomlLiteral(value.toUInt())
    }

    override fun encodeLong(value: Long) {
        delegate.element = TomlLiteral(value.toULong())
    }
}

// -------- AbstractTomlElementCompositeEncoder --------

private abstract class AbstractTomlElementCompositeEncoder(
    delegate: AbstractTomlElementEncoder
) : AbstractTomlElementEncoder(delegate.toml), TomlCompositeEncoder {
    final override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
        return if (descriptor.getElementDescriptor(index).isUnsignedInteger) {
            TomlElementInlineElementEncoder(
                parentDescriptor = descriptor,
                elementIndex = index,
                delegate = this
            )
        } else {
            this
        }
    }

    final override fun endStructure(descriptor: SerialDescriptor) {}
}

// -------- TomlElementInlineElementEncoder --------

private class TomlElementInlineElementEncoder(
    parentDescriptor: SerialDescriptor,
    elementIndex: Int,
    delegate: AbstractTomlElementCompositeEncoder
) : AbstractTomlInlineElementEncoder<AbstractTomlElementCompositeEncoder>(parentDescriptor, elementIndex, delegate) {
    override fun encodeByte(value: Byte) {
        delegate.encodeElement(parentDescriptor, elementIndex) {
            delegate.element = TomlLiteral(value.toUByte())
        }
    }

    override fun encodeShort(value: Short) {
        delegate.encodeElement(parentDescriptor, elementIndex) {
            delegate.element = TomlLiteral(value.toUShort())
        }
    }

    override fun encodeInt(value: Int) {
        delegate.encodeElement(parentDescriptor, elementIndex) {
            delegate.element = TomlLiteral(value.toUInt())
        }
    }

    override fun encodeLong(value: Long) {
        delegate.encodeElement(parentDescriptor, elementIndex) {
            delegate.element = TomlLiteral(value.toULong())
        }
    }
}

// -------- TomlElementClassEncoder --------

private class TomlElementClassEncoder(
    delegate: AbstractTomlElementEncoder,
    private val builder: MutableMap<String, TomlElement>,
    private val annotations: MutableMap<String, List<Annotation>>
) : AbstractTomlElementCompositeEncoder(delegate) {
    private lateinit var currentKey: String

    override fun encodeDiscriminatorElement(discriminator: String, serialName: String, isEmptyStructure: Boolean) {
        builder[discriminator] = TomlLiteral(serialName)
    }

    override fun beginElement(descriptor: SerialDescriptor, index: Int) {
        currentKey = descriptor.getElementName(index)
    }

    override fun endElement(descriptor: SerialDescriptor, index: Int) {
        val currentKey = currentKey
        builder[currentKey] = element
        // Record annotations only when the property actually carries some -- the
        // common case is none, and storing an empty list for every property
        // means a list concatenation and a map put per property. A missing key
        // already means "no annotations" to the emitter (this is what the parser
        // produces too), so skip both. Concatenate only when both sides are
        // non-empty; otherwise the non-empty side is reused as-is.
        val propertyAnnotations = descriptor.getElementAnnotations(index)
        val intrinsicAnnotations = descriptor.getElementDescriptor(index).annotations
        when {
            propertyAnnotations.isEmpty() -> {
                if (intrinsicAnnotations.isNotEmpty()) {
                    annotations[currentKey] = intrinsicAnnotations
                }
            }
            intrinsicAnnotations.isEmpty() -> {
                annotations[currentKey] = propertyAnnotations
            }
            else -> {
                annotations[currentKey] = propertyAnnotations + intrinsicAnnotations
            }
        }
    }
}

// -------- TomlElementMapEncoder --------

private class TomlElementMapEncoder(
    delegate: AbstractTomlElementEncoder,
    private val builder: MutableMap<String, TomlElement>,
    private val annotations: MutableMap<String, List<Annotation>>
) : AbstractTomlElementCompositeEncoder(delegate) {
    private var isKey: Boolean = true

    private lateinit var currentKey: String

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        if (isKey) { // This element is key.
            currentKey = value.toTomlKey()
        } else { // This element is value (of the entry).
            encodeSerializableValue(serializer, value)
            val currentKey = currentKey
            builder[currentKey] = element
            annotations[currentKey] = descriptor.getElementDescriptor(index).annotations
        }
        isKey = !isKey
    }

    override fun encodeDiscriminatorElement(discriminator: String, serialName: String, isEmptyStructure: Boolean) {
        throwPolymorphicCollection()
    }

    // This shouldn't be called.
    override fun beginElement(descriptor: SerialDescriptor, index: Int) {}

    // This shouldn't be called.
    override fun endElement(descriptor: SerialDescriptor, index: Int) {}
}

// -------- TomlElementArrayEncoder --------

private class TomlElementArrayEncoder(
    delegate: AbstractTomlElementEncoder,
    private val builder: MutableList<TomlElement>,
    private val annotations: MutableList<List<Annotation>>
) : AbstractTomlElementCompositeEncoder(delegate) {
    override fun encodeDiscriminatorElement(discriminator: String, serialName: String, isEmptyStructure: Boolean) {
        throwPolymorphicCollection()
    }

    override fun beginElement(descriptor: SerialDescriptor, index: Int) {}

    override fun endElement(descriptor: SerialDescriptor, index: Int) {
        builder.add(element)
        annotations.add(descriptor.getElementDescriptor(index).annotations)
    }
}

// -------- Utils --------

private fun AbstractTomlElementEncoder.beginStructurePolymorphically(
    descriptor: SerialDescriptor
): CompositeEncoder {
    val compositeEncoder = when (val kind = descriptor.kind) {
        CLASS, is PolymorphicKind, OBJECT -> {
            val builder = mutableMapOf<String, TomlElement>()
            val annotations = mutableMapOf<String, List<Annotation>>()
            element = TomlTable(builder, annotations)
            TomlElementClassEncoder(this, builder, annotations)
        }
        else -> throwUnsupportedSerialKind(kind)
    }
    onBeginStructurePolymorphically(
        compositeEncoder = compositeEncoder,
        descriptor = descriptor,
        isEmptyStructure = descriptor.elementsCount == 0
    )
    return compositeEncoder
}

// Currently polymorphic collection-like types are not supported.
@Suppress("UNUSED_PARAMETER")
private fun AbstractTomlElementEncoder.beginCollectionPolymorphically(
    descriptor: SerialDescriptor,
    collectionSize: Int
): CompositeEncoder {
    return when (val kind = descriptor.kind) {
        LIST -> {
            val builder = mutableListOf<TomlElement>()
            val annotations = mutableListOf<List<Annotation>>()
            element = TomlArray(builder, annotations)
            TomlElementArrayEncoder(this, builder, annotations)
        }
        MAP -> {
            val builder = mutableMapOf<String, TomlElement>()
            val annotations = mutableMapOf<String, List<Annotation>>()
            element = TomlTable(builder, annotations)
            TomlElementMapEncoder(this, builder, annotations)
        }
        else -> throwUnsupportedSerialKind(kind)
    }
}
