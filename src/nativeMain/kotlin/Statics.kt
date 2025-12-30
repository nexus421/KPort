import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import platform.posix.getenv

/**
 * JSON configuration for serialization.
 */
val json = Json {
    encodeDefaults = true
    prettyPrint = true
}

/**
 * Base directory for configuration files (~/.config/kport).
 * Falls back to current directory if HOME is not set.
 */
@OptIn(ExperimentalForeignApi::class)
val workingDir = OkioFile((getenv("HOME")?.toKString() ?: ".").let { "$it/.config/kport" })

/**
 * Full path to the config.json file.
 */
val configDir = OkioFile(workingDir, "config.json")

/**
 * Global configuration object, loaded lazily from configDir.
 */
val config: Config by lazy {
    try {
        json.decodeFromString<Config>(configDir.readText()).also {
            println("Configuration loaded from $configDir")
        }
    } catch (e: Exception) {
        println("Warning: No configuration found or could not be read (${e.message}). Using defaults.")
        Config(rules = listOf(Rule(1025, 81, "10.123.123.12")))
    }
}