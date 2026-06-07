package test

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.eav.tomlkt.Toml
import kotlinx.serialization.Serializable
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// region Models

// The serializable models the encode benchmark renders. They are plain Kotlin
// data classes so the same instance can be handed to every library: tomlkt
// reads the generated `.serializer()` and jackson reads them through its Kotlin
// module. The shapes mirror the parsing benchmark's samples so the two halves
// stress comparable structure.

@Serializable
data class ServerConf(val ruby: Boolean, val python: Boolean, val clean: Boolean)

@Serializable
data class Server(val steps: List<String>, val conf: ServerConf)

@Serializable
data class Environment(val serverA: Server, val serverB: Server)

// `config`: nested tables, string arrays and booleans (cf. invented_server_configuration).
@Serializable
data class ServerConfig(val production: Environment, val staging: Environment)

// `content`: a handful of large text fields. Exercises basic-string escaping
// throughput, since a plain String with newlines is emitted as an escaped
// basic string (cf. content_heavy).
@Serializable
data class Document(val title: String, val paragraphs: List<String>, val body: String)

@Serializable
data class LineItem(val sku: String, val description: String, val quantity: Int, val price: Double)

// `wide`: a long array of tables (cf. yaml_invoice_example's arrays-of-tables).
@Serializable
data class Invoice(val number: Int, val items: List<LineItem>)

private const val LOREM =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Duis quis dolor " +
        "quis orci vestibulum tempor. Donec sodales urna quam, eget consectetur " +
        "risus venenatis a. Class aptent taciti sociosqu ad litora torquent per " +
        "conubia nostra, per inceptos himenaeos."

object Samples {
    val config: ServerConfig = run {
        val steps = listOf("npm", "bundle", "audit")
        val server = Server(steps, ServerConf(ruby = true, python = false, clean = true))
        val env = Environment(server, server.copy(conf = ServerConf(true, true, false)))
        ServerConfig(production = env, staging = env)
    }

    val content: Document = run {
        // ~30 paragraphs joined by blank lines gives an ~18 KB body, matching
        // the parsing sample, with internal newlines that force escaping.
        val paragraphs = List(8) { LOREM }
        Document(
            title = "On the Origin of Placeholder Text",
            paragraphs = paragraphs,
            body = List(30) { LOREM }.joinToString("\n\n")
        )
    }

    val wide: Invoice = run {
        val items = List(100) { i ->
            LineItem(
                sku = "SKU-$i",
                description = "Item number $i of the invoice",
                quantity = i + 1,
                price = (i + 1) * 1.99
            )
        }
        Invoice(number = 12345, items = items)
    }
}

// endregion

@State(Scope.Thread)
object EncodeObjects {
    val tomlkt = Toml
    val jackson = TomlMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }
    val ktoml = com.akuleshov7.ktoml.Toml
}

/*
    Encoding counterpart to the parsing DecodeBenchmark. Each @Benchmark
    serializes one in-memory model (for tomlkt, Toml.encodeToString) to a TOML
    string and compares tomlkt against jackson (TomlMapper.writeValueAsString)
    and ktoml. ktoml is the other kotlinx.serialization TOML format -- and so
    tomlkt's closest peer -- but is an order of magnitude slower, so expect a
    wide spread in the results. night-config is omitted because its writer
    consumes a hand-built Config rather than a POJO; tomlj is parse-only with no
    writer at all.

    The model is chosen through the `sample` @Param, each a distinct emit
    workload mirroring a parsing sample:

        config    nested tables, string arrays, booleans      (small, structural)
        content   large text fields, basic-string escaping     (emit throughput)
        wide      a 100-element array of tables                (arrays-of-tables)

    Run a single sample with, e.g., `-p sample=content`. The encode call for each
    library is captured once in @Setup so the measured method only pays for the
    serialization, not the model lookup or the per-sample type dispatch.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Threads(4)
@Fork(1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
class EncodeBenchmark {
    @Param(
        "config",
        "content",
        "wide",
    )
    lateinit var sample: String

    private lateinit var tomlktEncode: () -> String
    private lateinit var jacksonEncode: () -> String
    private lateinit var ktomlEncode: () -> String

    @Setup
    fun selectSample() {
        when (sample) {
            "config" -> {
                val model = Samples.config
                tomlktEncode = { EncodeObjects.tomlkt.encodeToString(ServerConfig.serializer(), model) }
                jacksonEncode = { EncodeObjects.jackson.writeValueAsString(model) }
                ktomlEncode = { EncodeObjects.ktoml.encodeToString(ServerConfig.serializer(), model) }
            }
            "content" -> {
                val model = Samples.content
                tomlktEncode = { EncodeObjects.tomlkt.encodeToString(Document.serializer(), model) }
                jacksonEncode = { EncodeObjects.jackson.writeValueAsString(model) }
                ktomlEncode = { EncodeObjects.ktoml.encodeToString(Document.serializer(), model) }
            }
            "wide" -> {
                val model = Samples.wide
                tomlktEncode = { EncodeObjects.tomlkt.encodeToString(Invoice.serializer(), model) }
                jacksonEncode = { EncodeObjects.jackson.writeValueAsString(model) }
                ktomlEncode = { EncodeObjects.ktoml.encodeToString(Invoice.serializer(), model) }
            }
            else -> error("unknown sample: $sample")
        }
    }

    @Benchmark
    fun tomlkt(hole: Blackhole) {
        hole.consume(tomlktEncode())
    }

    @Benchmark
    fun jackson(hole: Blackhole) {
        hole.consume(jacksonEncode())
    }

    @Benchmark
    fun ktoml(hole: Blackhole) {
        hole.consume(ktomlEncode())
    }
}
