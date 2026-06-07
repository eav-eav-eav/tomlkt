package test

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.eav.tomlkt.TomlStringReader
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

@State(Scope.Thread)
object TomlObjects {
    val tomlkt = dev.eav.tomlkt.Toml
    val ktoml = com.akuleshov7.ktoml.Toml
    val jackson = com.fasterxml.jackson.dataformat.toml.TomlMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }
    val night = com.electronwill.nightconfig.toml.TomlParser()
}

/*
    Parsing benchmark over a representative subset of the eno-lang sample corpus
    (https://github.com/eno-lang/benchmarks/tree/main/samples). Each @Benchmark
    measures parsing one TOML file to its in-memory tree (for tomlkt, that is
    Toml.parseToTomlTable) and compares tomlkt against other JVM TOML libraries:
    jackson, night-config, ktoml and tomlj. ktoml and tomlj are an order of
    magnitude slower than the rest, so expect a wide spread in the results.

    The TOML files live in src/jmh/resources/samples and are selected through
    the `sample` @Param. Five samples exist, each covering a distinct parser
    workload, but only the first three run by default to keep a full run fast:

        invented_server_configuration    539 B  nested tables, string arrays, booleans
        yaml_invoice_example             651 B  mixed scalars, arrays-of-tables, multiline literals
        content_heavy                 18971 B  large input, multiline basic string throughput
        escape_heavy                  13510 B  large basic string dense with escape sequences   (opt-in)
        literal_heavy                 18971 B  large multiline literal string throughput        (opt-in)

    The two heavy string samples are commented out in the @Param list below;
    uncomment either to include it. Run a single sample with, e.g.,
    `-p sample=content_heavy`.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Threads(4)
@Fork(1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
class DecodeBenchmark {
    @Param(
        "invented_server_configuration",
        "yaml_invoice_example",
        "content_heavy",
        // "escape_heavy",
        // "literal_heavy",
    )
    lateinit var sample: String

    private lateinit var content: String

    @Setup
    fun loadSample() {
        val resource = "/samples/$sample.toml"
        content = checkNotNull(javaClass.getResourceAsStream(resource)) {
            "missing sample resource: $resource"
        }.bufferedReader().use { it.readText() }
    }

    @Benchmark
    fun tomlkt(hole: Blackhole) {
        hole.consume(TomlObjects.tomlkt.parseToTomlTable(content))
    }

    @Benchmark
    fun tomlktReader(hole: Blackhole) {
        hole.consume(TomlObjects.tomlkt.parseToTomlTable(TomlStringReader(content)))
    }

    @Benchmark
    fun ktoml(hole: Blackhole) {
        hole.consume(TomlObjects.ktoml.tomlParser.parseString(content))
    }

    @Benchmark
    fun jackson(hole: Blackhole) {
        hole.consume(TomlObjects.jackson.readTree(content))
    }

    @Benchmark
    fun night(hole: Blackhole) {
        hole.consume(TomlObjects.night.parse(content))
    }

    @Benchmark
    fun tomlj(hole: Blackhole) {
        hole.consume(org.tomlj.Toml.parse(content))
    }
}
