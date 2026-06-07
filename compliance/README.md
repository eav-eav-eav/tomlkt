# Compliance harness

This module is a throwaway adapter that runs tomlkt against the
[toml-test](https://github.com/toml-lang/toml-test) conformance suite (v2). It is
not part of the published library and is not on the `:core` dependency path.

`src/main/kotlin/harness/Main.kt` is a small JVM program that speaks the
toml-test protocol:

- **decode** (default): reads TOML on stdin, writes toml-test "tagged" JSON on
  stdout.
- **encode**: reads tagged JSON on stdin, writes TOML on stdout.

On any failure the process exits non-zero, which is how toml-test asserts that
`invalid/` inputs are rejected. Input bytes are decoded as strict UTF-8, so
malformed UTF-8 and surrogate code points are rejected (the `encoding/*` cases).
Date-time values are normalized to include seconds in the decode-path JSON, which
toml-test re-parses with a strict layout.

## Prerequisites

Install the `toml-test` runner, **v2** (Go toolchain required — the v2 CLI
differs from v1):

```bash
go install github.com/toml-lang/toml-test/v2/cmd/toml-test@v2.2.0
# the binary lands in $(go env GOPATH)/bin, usually ~/go/bin/toml-test
```

## Run via Gradle (recommended)

`complianceTest` builds the harness and runs the whole suite (decoder + encoder)
against it; `complianceDecode` runs only the decoder side for faster iteration.
v2 runs the encoder cases alongside the decoder in one pass, so there is no
standalone encoder task.

```bash
./gradlew :compliance:complianceTest
```

Configure with project properties:

- `-PtomlVersion=1.0` — TOML version to test, `1.0` or `1.1` (default `1.1`).
- `-PtomlTest=/path/to/toml-test` — the runner. Otherwise resolved from
  `$TOML_TEST`, then `~/go/bin/toml-test`, then `PATH`.

```bash
./gradlew :compliance:complianceTest -PtomlVersion=1.0
```

## Run toml-test manually

`installDist` produces a launcher script under
`build/install/compliance/bin/compliance`:

```bash
./gradlew :compliance:installDist
```

Re-run this after any change to `:core` or the harness so the launcher picks up
the new classes.

The harness takes the direction (`decode` / `encode`) as its first argument.
Point toml-test at it via the v2 `test` subcommand (the encoder run still needs a
`-decoder` for round-trip verification):

```bash
HARNESS="$(pwd)/compliance/build/install/compliance/bin/compliance"

# Decoder (parser) tests
toml-test test -decoder="$HARNESS decode" -toml 1.1

# Decoder + encoder (writer) tests
toml-test test -decoder="$HARNESS decode" -encoder="$HARNESS encode" -toml 1.1
```

Swap `-toml 1.1` for `-toml 1.0` to test against TOML 1.0 instead. Run a single
case with `-run`, e.g. `-run valid/datetime/local-time`.

## Current status (TOML 1.1)

Against toml-test v2.2.0 the whole suite is green: every `valid/`, `invalid/`,
and encoder case passes (214 / 467 / 214).
