// BoltHelper.kt
@file:Suppress("MemberVisibilityCanBePrivate")

import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object BoltHelper {

    // Resolve BIN_DIR for IntelliJ plugin - binaries are in plugin resources
    // Temp dir to extract packaged helper binary
    private val BIN_DIR: Path = Paths.get(System.getProperty("java.io.tmpdir"))
        .resolve("boltdb-explorer-plugin")
    private fun getPlatformBinary(): Path {
        val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
        val archRaw = System.getProperty("os.arch").lowercase(Locale.ROOT)

        var bin = "bolthelper-"

        when {
            osName.contains("mac") || osName.contains("darwin") -> bin += "darwin-"
            osName.contains("linux") -> bin += "linux-"
            osName.contains("win") -> bin += "windows-"
            else -> throw IllegalStateException("Unsupported platform: $osName")
        }

        // Map common JVM arch names to expected names
        when (archRaw) {
            "aarch64", "arm64" -> bin += "arm64"
            "x86_64", "amd64", "x64" -> bin += "amd64"
            else -> throw IllegalStateException("Unsupported arch: $archRaw")
        }

        if (osName.contains("win")) {
            bin += ".exe"
        }

        // Extract binary from plugin resources if needed
        val binPath = BIN_DIR.resolve(bin)
        if (!Files.exists(binPath)) {
            extractBinaryFromResources(bin, binPath)
        }

        return binPath
    }

    private fun extractBinaryFromResources(binaryName: String, targetPath: Path) {
        // Create target directory
        Files.createDirectories(targetPath.parent)

        // Extract binary from plugin resources
        val resourcePath = "/bin/$binaryName"
        val inputStream = BoltHelper::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Binary not found in plugin resources: $resourcePath")

        inputStream.use { input ->
            Files.copy(input, targetPath)
        }

        // Make binary executable
        targetPath.toFile().setExecutable(true)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    @Throws(Exception::class)
    private fun execJson(
        args: List<String>,
        workingDir: File? = null,
        extraEnv: Map<String, String>? = null
    ): JsonElement {
        val bin = getPlatformBinary().toString()
        val startTime = System.currentTimeMillis()

        val pb = ProcessBuilder(listOf(bin) + args)
        pb.redirectErrorStream(false)
        if (workingDir != null) pb.directory(workingDir)
        if (extraEnv != null) pb.environment().putAll(extraEnv)

        val proc = pb.start()

        val outCollector = StreamCollector(proc.inputStream)
        val errCollector = StreamCollector(proc.errorStream)
        val tOut = Thread(outCollector, "bolthelper-stdout")
        val tErr = Thread(errCollector, "bolthelper-stderr")
        tOut.start()
        tErr.start()

        val exitCode: Int = try {
            proc.waitFor()
        } finally {
            tOut.join()
            tErr.join()
        }

        val duration = System.currentTimeMillis() - startTime

        val stderr = errCollector.result()
        val stdout = outCollector.result()
        if (exitCode == 0) {
            return json.parseToJsonElement(stdout)
        } else {
            if (stderr.isNotBlank()) throw RuntimeException(stderr.trim())
            else throw RuntimeException("exit code $exitCode")
        }
    }

    // Public API

    @Throws(Exception::class)
    fun getMeta(dbPath: String): JsonElement = execJson(listOf("meta", "--db", dbPath))

    suspend fun isBoltDB(dbPath: String): Boolean = try {
        getMeta(dbPath); true
    } catch (_: Exception) { false }

    @Throws(Exception::class)
    fun listBuckets(dbPath: String, bucketPath: String): JsonElement =
        execJson(listOf("lsb", "--db", dbPath, "--path", bucketPath))

    data class ListKeysOptions(
        val prefix: String? = null,
        val limit: Int? = null,
        val afterKey: String? = null
    )

    @Throws(Exception::class)
    fun listKeys(dbPath: String, bucketPath: String, opts: ListKeysOptions = ListKeysOptions()): JsonElement {
        val label = "boltClient-listKeys-${if (bucketPath.isBlank()) "root" else bucketPath}"
        val start = System.nanoTime()

        val args = mutableListOf("lsk", "--db", dbPath, "--path", bucketPath)
        if (!opts.prefix.isNullOrEmpty()) args.addAll(listOf("--prefix", opts.prefix))
        if (opts.limit != null) args.addAll(listOf("--limit", opts.limit.toString()))
        if (!opts.afterKey.isNullOrEmpty()) args.addAll(listOf("--after-key", opts.afterKey))

        val result = execJson(args)
        val durationMs = (System.nanoTime() - start) / 1_000_000
        println("$label: ${durationMs}ms")
        return result
    }

    @Throws(Exception::class)
    fun readHead(dbPath: String, bucketPath: String, keyBase64: String, n: Int = 65536): JsonElement =
        execJson(
            listOf(
                "get", "--db", dbPath, "--path", bucketPath, "--key", keyBase64,
                "--mode", "head", "--n", n.toString()
            )
        )

    @Throws(Exception::class)
    fun saveToFile(dbPath: String, bucketPath: String, keyBase64: String, outPath: String): JsonElement =
        execJson(
            listOf(
                "get", "--db", dbPath, "--path", bucketPath, "--key", keyBase64,
                "--mode", "save", "--out", outPath
            )
        )

    @Throws(Exception::class)
    fun exportBucket(dbPath: String, bucketPath: String?, outPath: String, prefix: String? = null): JsonElement {
        val args = mutableListOf("export", "--db", dbPath, "--out", outPath)
        if (!bucketPath.isNullOrBlank()) args.addAll(listOf("--path", bucketPath))
        if (!prefix.isNullOrBlank()) args.addAll(listOf("--prefix", prefix))
        return execJson(args)
    }

    @Throws(Exception::class)
    fun search(dbPath: String, query: String, limit: Int = 100, caseSensitive: Boolean = false): JsonElement {
        val args = mutableListOf("search", "--db", dbPath, "--query", query, "--limit", limit.toString())
        if (caseSensitive) args.add("-case-sensitive")
        return execJson(args)
    }

    @Throws(Exception::class)
    fun createBucket(dbPath: String, bucketPath: String): JsonElement =
        execJson(listOf("write", "--db", dbPath, "--op", "create-bucket", "--path", bucketPath))

    @Throws(Exception::class)
    fun putKeyValue(dbPath: String, bucketPath: String, keyBase64: String, valueBase64: String): JsonElement =
        execJson(
            listOf(
                "write", "--db", dbPath, "--op", "put",
                "--path", bucketPath, "--key", keyBase64, "--value", valueBase64
            )
        )

    @Throws(Exception::class)
    fun deleteKey(dbPath: String, bucketPath: String, keyBase64: String): JsonElement =
        execJson(
            listOf(
                "write", "--db", dbPath, "--op", "delete-key",
                "--path", bucketPath, "--key", keyBase64
            )
        )

    @Throws(Exception::class)
    fun deleteBucket(dbPath: String, bucketPath: String): JsonElement =
        execJson(listOf("write", "--db", dbPath, "--op", "delete-bucket", "--path", bucketPath))

    // Helpers
    private class StreamCollector(private val input: InputStream) : Runnable {
        private val sb = StringBuilder()
        override fun run() {
            input.bufferedReader().useLines { lines ->
                lines.forEach { line -> sb.append(line).append('\n') }
            }
        }
        fun result(): String = sb.toString()
    }
}