# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

tomlkt is a [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) format plugin for TOML 1.0.0, published to Maven Central as `dev.eav.tomlkt:tomlkt`. It is a Kotlin Multiplatform library targeting JVM, JS (IR), Wasm/JS, and a wide range of Kotlin/Native targets (mingw, macos, ios, linux). The public package is `dev.eav.tomlkt`.

## Common Commands

Build and test run through the Gradle wrapper. The publishable code lives in the `:core` module (`:benchmark` is JMH-only).

```bash
./gradlew core:build                       # compile + test all targets
./gradlew core:check                        # full CI check (tests + detekt) — what CI runs
./gradlew core:allTests                     # run tests on all targets
./gradlew core:jvmTest                       # run JVM tests only (fastest iteration loop)
./gradlew core:jsTest core:wasmJsTest        # JS / Wasm tests
./gradlew core:detektMetadataMain            # static analysis (uses format/detekt.yml)
```

Run a single test class/method via the JUnit platform filter (JVM target):

```bash
./gradlew core:jvmTest --tests "dev.eav.tomlkt.IntegerTest"
./gradlew core:jvmTest --tests "dev.eav.tomlkt.IntegerTest.testNegativeNumber"
```

Most tests live in `commonTest` and run on every target; `jvmTest` additionally covers stream I/O (`StreamTest`). When iterating, prefer `core:jvmTest` for speed, but run `core:allTests` before considering a change complete since platform-specific `actual` implementations differ.

Docs are generated with Dokka (`./gradlew core:dokkaGenerate`, output in `docs/`).

## Architecture

The central design is that everything flows through an intermediate representation, `TomlElement`, rather than converting models directly to/from text:

- **Encoding:** Model → `TomlElementEncoder` → `TomlElement` → `TomlElementEmitter` → text
- **Decoding:** text → `TomlElementParser` → `TomlElement` → `TomlElementDecoder` → Model

`Toml.kt` is the public entry point (`Toml`, `Toml { }` factory, `encodeToString`/`decodeFromString`/`parseToTomlTable`). It wires the four internal stages together. Understanding any encode/decode behavior usually means reading the relevant stage in `core/src/commonMain/kotlin/dev/eav/tomlkt/internal/`:

- `parser/TomlElementParser.kt` — hand-written TOML lexer/parser; builds a tree of `TreeNode` (`KeyNode`/`ArrayNode`/`ValueNode`) then a `TomlTable`. Character-class constraints (e.g. `BareKeyRegex`, `DecimalConstraints`) live in `internal/StringUtils.kt`.
- `encoder/` — `AbstractTomlEncoder` + `TomlElementEncoder` implement the kotlinx.serialization `Encoder`/`CompositeEncoder` SPI, turning a serializable model into a `TomlElement`.
- `decoder/` — `AbstractTomlDecoder` + `TomlElementDecoder` implement the `Decoder` SPI, turning a `TomlElement` into a model.
- `emitter/TomlElementEmitter.kt` — renders a `TomlElement` to TOML text via the `TomlWriter` abstraction.

`TomlElement.kt` (the largest file) defines the sealed hierarchy: `TomlNull`, `TomlLiteral`, `TomlArray`, `TomlTable`, plus conversion/accessor extensions (e.g. `TomlTable["a", "b"]` path access, `toTomlLiteral()`). `TomlElementBuilders.kt` provides the `buildTomlTable { }` DSL. These are the only types meant to be (de)serialized directly, and only via `Toml`.

### Annotations drive formatting

User-facing formatting is controlled by annotations in `Annotations.kt` applied to `@Serializable` properties: `@TomlComment`, `@TomlMultilineString`, `@TomlLiteralString`, `@TomlInline`, `@TomlBlockArray`, `@TomlInteger` (base/representation). These are metadata consumed only by the encoder/emitter — **the parser ignores them**, so a parse→emit round trip does not preserve annotation-driven formatting.

### Configuration

`TomlConfig.kt` defines the knobs settable in `Toml { }`: `serializersModule`, `explicitNulls`, `classDiscriminator`, `indentation` (`TomlIndentation`), `itemsPerLineInBlockArray`, `uppercaseInteger`, `ignoreUnknownKeys`.

### Platform split: `expect`/`actual` and source-set hierarchy

The source-set graph is non-default. There is an intermediate `kotlinxMain` set that `dependsOn(commonMain)` and adds a dependency on `kotlinx-datetime`; **every target except JVM** (`jsMain`, all native, `wasmJsMain`) depends on `kotlinxMain`. JVM instead uses `java.time`. This is how date-time `expect`/`actual` types are backed differently per platform:

- `NativeDateTime.common.kt` declares `expect` typealiases (`NativeLocalDateTime`, `NativeOffsetDateTime`, `NativeLocalDate`, `NativeLocalTime`).
- `jvmMain/NativeDateTime.jvm.kt` maps them to `java.time.*`.
- `kotlinxMain/NativeDateTime.kotlinx.kt` maps them to `kotlinx.datetime.*` (note `TomlOffsetDateTime` ↔ `Instant`).

`TomlDateTime.kt` exposes the public `TomlLocalDateTime`/etc. as the serializable intermediate, with `TomlLiteral(...)` ↔ `toLocalDateTime()` conversions.

Stream/reader/writer I/O is also platform-aware: `TomlReader`/`TomlWriter` are common abstractions; `jvmMain` adds `TomlStreams.kt`, `TomlNativeReader.kt`, `TomlNativeWriter.kt` for `InputStream`/`OutputStream` support.

## Conventions

- The module uses `explicitApi()` — every public declaration needs an explicit visibility modifier and the compiler enforces it.
- Numerous opt-ins are enabled project-wide in `core/build.gradle.kts` (contracts, `ExperimentalSerializationApi`, `InternalSerializationApi`, the internal `@TomlSpecific` marker). When you hit an opt-in error, prefer adding to the existing `languageSettings` block over scattering `@OptIn`.
- Copyright header (Apache-2.0, "Copyright 2026 Loney Chou") is present on every source file — keep it on new files.
- Version, group, and dependency versions are centralized in `gradle.properties`.
