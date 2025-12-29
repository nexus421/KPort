import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.getenv

/**
 * JSON configuration for serialization.
 */
val json = Json {
    encodeDefaults = true
}

/**
 * Base directory for configuration files (~/.config/kport).
 * Falls back to current directory if HOME is not set.
 */
@OptIn(ExperimentalForeignApi::class)
val workingDir = (getenv("HOME")?.toKString() ?: ".").let { "$it/.config/kport" }.toPath()

/**
 * Full path to the config.json file.
 */
val configDir = workingDir.resolve("config.json")

/**
 * Global configuration object, loaded lazily from configDir.
 */
val config: Config by lazy {
    try {
        FileSystem.SYSTEM.read(configDir) { readUtf8() }.let {
            json.decodeFromString<Config>(it) }.also {
            println("Configuration loaded from $configDir")
        }
    } catch (e: Exception) {
        println("Warning: No configuration found or could not be read (${e.message}). Using defaults.")
        Config()
    }
}