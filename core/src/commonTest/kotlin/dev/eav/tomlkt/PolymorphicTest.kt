package dev.eav.tomlkt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test

class PolymorphicTest {
    @Serializable
    data class M1(
        val b: B1
    )

    @Serializable
    sealed class B1 {
        abstract val i: Int
    }

    @Serializable
    data class S1(
        override val i: Int
    ) : B1()

    val m11 = M1(
        b = S1(
            i = 0
        )
    )

    val s11 = """
        [b]
        type = "dev.eav.tomlkt.PolymorphicTest.S1"
        i = 0
    """.trimIndent()

    @Test
    fun encodeClosedPolymorphismRegularly() {
        testEncode(M1.serializer(), m11, s11)
    }

    @Test
    fun decodeClosedPolymorphismRegularly() {
        testDecode(M1.serializer(), s11, m11)
    }

    val e11 = TomlTable(
        map = mapOf(
            "b" to TomlTable(
                map = mapOf(
                    "type" to "dev.eav.tomlkt.PolymorphicTest.S1",
                    "i" to 0
                )
            )
        )
    )

    @Test
    fun encodeClosedPolymorphismToTomlElement() {
        testEncodeTomlElement(M1.serializer(), m11, e11)
    }

    @Serializable
    data class M2(
        val b: B1
    )

    @Serializable
    data class S2(
        override val i: Int,
        val c: C
    ) : B1()

    @Serializable
    data class C(
        val s: String
    )

    val m21 = M2(
        b = S2(
            i = 0,
            c = C(
                s = ""
            )
        )
    )

    val s21 = """
        [b]
        type = "dev.eav.tomlkt.PolymorphicTest.S2"
        i = 0
        
        [b.c]
        s = ""
    """.trimIndent()

    @Test
    fun encodeClosedPolymorphismWithExtraProperties() {
        testEncode(M2.serializer(), m21, s21)
    }

    @Test
    fun decodeClosedPolymorphismWithExtraProperties() {
        testDecode(M2.serializer(), s21, m21)
    }

    @Serializable
    data class M3(
        val b: B1
    )

    @Serializable
    data object O1 : B1() {
        override val i: Int
            get() = 0
    }

    val m31 = M3(
        b = O1
    )

    val s31 = """
        [b]
        type = "dev.eav.tomlkt.PolymorphicTest.O1"
    """.trimIndent()

    @Test
    fun encodeClosedPolymorphismWithObject() {
        testEncode(M3.serializer(), m31, s31)
    }

    @Test
    fun decodeClosedPolymorphismWithObject() {
        testDecode(M3.serializer(), s31, m31)
    }

    @Serializable
    data class M4(
        val b: B2
    )

    @Serializable
    abstract class B2 {
        abstract val i: Int
    }

    @Serializable
    data class S3(
        override val i: Int
    ) : B2()

    val t1 = Toml {
        serializersModule = SerializersModule {
            polymorphic(B2::class) {
                subclass(S3::class, S3.serializer())
            }
        }
    }

    val m41 = M4(
        b = S3(
            i = -1
        )
    )

    val s41 = """
        [b]
        type = "dev.eav.tomlkt.PolymorphicTest.S3"
        i = -1
    """.trimIndent()

    @Test
    fun encodeOpenPolymorphismRegularly() {
        testEncode(M4.serializer(), m41, s41, t1)
    }

    @Test
    fun decodeOpenPolymorphismRegularly() {
        testDecode(M4.serializer(), s41, m41, t1)
    }

    val e41 = TomlTable(
        map = mapOf(
            "b" to TomlTable(
                map = mapOf(
                    "type" to "dev.eav.tomlkt.PolymorphicTest.S3",
                    "i" to -1
                )
            )
        )
    )

    @Test
    fun encodeOpenPolymorphismToTomlElement() {
        testEncodeTomlElement(M4.serializer(), m41, e41, t1)
    }

    @Serializable
    data class M5(
        val b: B2
    )

    @Serializable
    data class S4(
        val c: C,
        override val i: Int
    ) : B2()

    val t2 = Toml {
        serializersModule = SerializersModule {
            polymorphic(B2::class) {
                subclass(S4::class, S4.serializer())
            }
        }
    }

    val m51 = M5(
        b = S4(
            c = C(
                s = ""
            ),
            i = 1
        )
    )

    val s51 = """
        [b]
        type = "dev.eav.tomlkt.PolymorphicTest.S4"
        i = 1
        
        [b.c]
        s = ""
    """.trimIndent()

    @Test
    fun encodeOpenPolymorphismWithExtraProperties() {
        testEncode(M5.serializer(), m51, s51, t2)
    }

    @Test
    fun decodeOpenPolymorphismWithExtraProperties() {
        testDecode(M5.serializer(), s51, m51, t2)
    }

    @Serializable
    data class M6(
        val b: B2
    )

    @Serializable
    data object O2 : B2() {
        override val i: Int
            get() = Int.MAX_VALUE
    }

    val t3 = Toml {
        serializersModule = SerializersModule {
            polymorphic(B2::class) {
                subclass(O2::class, O2.serializer())
            }
        }
    }

    val m61 = M6(
        b = O2
    )

    val s61 = """
        [b]
        type = "dev.eav.tomlkt.PolymorphicTest.O2"
    """.trimIndent()

    @Test
    fun encodeOpenPolymorphismWithObject() {
        testEncode(M6.serializer(), m61, s61, t3)
    }

    @Test
    fun decodeOpenPolymorphismWithObject() {
        testDecode(M6.serializer(), s61, m61, t3)
    }

    @Serializable
    data class M7(
        val b: B1
    )

    @Serializable
    @SerialName("\n")
    data class S5(
        override val i: Int
    ) : B1()

    val m71 = M7(
        b = S5(
            i = 0
        )
    )

    val s71 = """
        [b]
        type = "\n"
        i = 0
    """.trimIndent()

    @Test
    fun encodeWithCustomSerialName() {
        testEncode(M7.serializer(), m71, s71)
    }

    @Test
    fun decodeWithCustomSerialName() {
        testDecode(M7.serializer(), s71, m71)
    }

    val t4 = Toml {
        classDiscriminator = "class"
    }

    val s72 = """
        [b]
        class = "\n"
        i = 0
    """.trimIndent()

    @Test
    fun encodeWithCustomDiscriminator1() {
        testEncode(M7.serializer(), m71, s72, t4)
    }

    @Test
    fun decodeWithCustomDiscriminator1() {
        testDecode(M7.serializer(), s72, m71, t4)
    }

    @Serializable
    data class M8(
        val b: B3
    )

    @Serializable
    @TomlClassDiscriminator("t")
    sealed class B3(val i: Int)

    @Serializable
    data class S6(
        val s: String
    ) : B3(0)

    val m81 = M8(
        b = S6(
            s = " "
        )
    )

    val s81 = """
        [b]
        t = "dev.eav.tomlkt.PolymorphicTest.S6"
        i = 0
        s = " "
    """.trimIndent()

    @Test
    fun encodeWithCustomDiscriminator2() {
        testEncode(M8.serializer(), m81, s81)
    }

    @Test
    fun decodeWithCustomDiscriminator2() {
        testDecode(M8.serializer(), s81, m81)
    }
}
