# tomlkt

[![Maven Central](https://img.shields.io/maven-central/v/dev.eav.tomlkt/tomlkt)](https://search.maven.org/artifact/dev.eav.tomlkt/tomlkt)
[![License](https://img.shields.io/github/license/eav-eav-eav/tomlkt)](http://www.apache.org/licenses/LICENSE-2.0)

Powerful and easy to use [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) plugin for [TOML 1.0.0 compliance](https://toml.io/en/v1.0.0)
encoding and decoding.

*If you find any problem along usage, please raise an [issue](https://github.com/eav-eav-eav/tomlkt/issues).* :wink:

## Setup

<details open>
<summary>Gradle Kotlin (build.gradle.kts)</summary>

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.eav.tomlkt:tomlkt:0.6.1")
}
```
</details>

<details>
<summary>Gradle Groovy (build.gradle)</summary>

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "dev.eav.tomlkt:tomlkt:0.6.1"
}
```
</details>

<details>
<summary>Maven (.pom)</summary>

```xml
<dependency>
  <groupId>dev.eav.tomlkt</groupId>
  <artifactId>tomlkt-jvm</artifactId>
  <version>0.6.1</version>
</dependency>
```
</details>

*Note: If your project is Kotlin Multiplatform, you can simply add this into commonMain
dependencies.*

## Quick Start

Write some config:

```toml
name = "Eav"

[account]
username = "Eav"
password = "123456"
```

Write some code:

```kotlin
@Serializable
data class User(
    val name: String,
    val account: Account?
)

@Serializable
data class Account(
    val username: String,
    val password: String
)

fun main() {
    // Here we use JVM.
    val tomlString = Paths.get("...").readText()
    // Either is OK, but to explicitly pass a serializer is faster.
    val user = Toml.decodeFromString(User.serializer(), tomlString)
    val user = Toml.decodeFromString<User>(tomlString)
    // That's it!

    // By the way if you need some configuration.
    val toml = Toml {
        ignoreUnknownKeys = true
    }
    // Use toml to apply the change.

    // Encoding.
    val anotherUser = User("Anonymous", null)
    // Again, better to explicitly pass a serializer.
    val config = Toml.encodeToString(User.serializer(), anotherUser)
    Paths.get("...").writeText(config)
    // Done.
}
```

## Supported TOML Features

| TOML Data Type          | Encoding           | Decoding           |
|-------------------------|--------------------|--------------------|
| [Comment](#Comment)     | :heavy_check_mark: | :heavy_check_mark: |
| Key                     | :heavy_check_mark: | :heavy_check_mark: |
| [String](#String)       | :heavy_check_mark: | :heavy_check_mark: |
| Integer                 | :heavy_check_mark: | :heavy_check_mark: |
| Float                   | :heavy_check_mark: | :heavy_check_mark: |
| Boolean                 | :heavy_check_mark: | :heavy_check_mark: |
| [Date Time](#Date-Time) | :heavy_check_mark: | :heavy_check_mark: |
| Array                   | :heavy_check_mark: | :heavy_check_mark: |
| Table                   | :heavy_check_mark: | :heavy_check_mark: |
| Inline Table            | :heavy_check_mark: | :heavy_check_mark: |
| Array of Tables         | :heavy_check_mark: | :heavy_check_mark: |

### Comment

Implemented as an annotation `@TomlComment` on **properties**:

```kotlin
class IntData(
    @TomlComment("""
        An integer,
        but is decoded into Long originally.
    """)
    val int: Int
)
IntData(10086)
```

The code above will be encoded into:

```toml
# An integer,
# but is decoded into Long originally.
int = 10086
```

### String

Basic strings are encoded as `"<content>"`. For multilines and literals, put an annotation as
below:

```kotlin
class MultilineStringData(
    @TomlMultilineString
    val multilineString: String
)
MultilineStringData("""
    Do, a deer, a female deer.
    Re, a drop of golden sun.
""".trimIndent())

class LiteralStringData(
    @TomlLiteralString
    val literalString: String
)
LiteralStringData("C:\\Users\\<User>\\.m2\\repositories")
```

The code above will be encoded into:

```toml
multilineString = """
Do, a deer, a female deer.
Re, a drop of golden sun."""

literalString = 'C:\Users\<User>\.m2\repositories'
```

You can use both annotations to get multiline literal string.

### Date Time

TOML supports several date time formats, so does tomlkt. tomlkt declares `TomlLocalDateTime`,
`TomlOffsetDateTime`, `TomlLocalDate`, `TomlLocalTime` as expect types with builtin support for
serialization (`@Serializable`).

The mapping of these expect types are as follows:

| tomlkt             | java.time      | kotlinx.datetime |
|--------------------|----------------|------------------|
| TomlLocalDateTime  | LocalDateTime  | LocalDateTime    |
| TomlOffsetDateTime | OffsetDateTime | Instant          |
| TomlLocalDate      | LocalDate      | LocalDate        |
| TomlLocalTime      | LocalTime      | LocalTime        |

[TomlLiteral](https://github.com/eav-eav-eav/tomlkt/tree/master/core/src/commonMain/kotlin/dev/eav/tomlkt/TomlElement.kt) is the default intermediate representation of a date time. For conversion,
simply use `TomlLiteral(TomlLocalDateTime)` to create a `TomlLiteral` from a `TomlLocalDateTime`
(true for other types), and `TomlLiteral.toLocalDateTime()` for the other way.

If you would like to provide a custom serializer, use `NativeLocalDateTime` and the like as raw
types.

### TomlElement

The working process of tomlkt:

* Encoding: Model → (TomlElementEncoder) → TomlElement → (TomlElementEmitter) → File.
* Decoding: File → (TomlElementParser) → TomlElement → (TomlElementDecoder) → Model.

As shown, if you already have a TOML file, you can have no model class, but still gain access
to every entry with the help of [TomlElement](https://github.com/eav-eav-eav/tomlkt/tree/master/core/src/commonMain/kotlin/dev/eav/tomlkt/TomlElement.kt). Simply parse the file with
`Toml.parseToTomlTable`, then access entries via various `TomlTable` extensions. Also, if you
are a framework author, you would also need the power of dynamic construction. For example, you
can construct a `TomlTable` like this:

```kotlin
val table = buildTomlTable {
    literal("title", "TOML Example")

    table("database") {
        literal("enabled", true)
        array("ports", TomlInline.Instance) {
            literal(8000)
        }
        
        table("temperature") {
            val commentForCpu = """
                The max temperature for the core.
                DO NOT SET OVER 100.0!
            """.trimIndent()

            literal("cpu", 79.5, TomlComment(commentForCpu))
        }
    }

    array("servers", TomlComment("Available servers.")) {
        table {
            literal("ip", "10.0.0.1")
            literal("role", "main")
        }
        table {
            literal("ip", "10.0.0.2")
        }
    }
}
```

The code above will be encoded into:

```toml
title = "TOML Example"

[database]
enabled = true
ports = [ 8000 ]

[database.temperature]
# The max temperature for the core.
# DO NOT SET OVER 100.0!
cpu = 79.5

# Available servers.

[[servers]]
ip = "10.0.0.1"
role = "main"

[[servers]]
ip = "10.0.0.2"
```

*Note: All the metadata coming from [annotations](https://github.com/eav-eav-eav/tomlkt/tree/master/core/src/commonMain/kotlin/dev/eav/tomlkt/Annotations.kt) is ignored in the parser, meaning that
despite you could parse a file into an element, you cannot emit it back to file fully as is.*

### Others

For other information, view [API docs](https://eav-eav-eav.github.io/tomlkt/).
