import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * Represents a file or directory using the Okio library for file operations.
 * This class provides utility methods for file and directory management,
 * including reading, writing, copying, moving, and deleting files.
 *
 * This class should give you JVM feelings when working with files and directories.
 *
 * @constructor Creates an `OkioFile` instance from a given path string.
 * @param pathString The string representation of the file path.
 */
class OkioFile(pathString: String) {

    /**
     * @constructor Creates an `OkioFile` instance by combining a parent directory
     * and a child path.
     * @param parent The parent directory as an `OkioFile`.
     * @param child The child path to be combined with the parent directory.
     */
    constructor(parent: OkioFile, child: String) : this(parent.path.toString().removeSuffix("/") + "/" + child)

    private val fs = FileSystem.SYSTEM
    val path = pathString.toPath()

    val name: String get() = path.name
    val parent: OkioFile? get() = path.parent?.let { OkioFile(it.toString()) }
    val absolutePath: String
        get() = try {
            fs.canonicalize(path).toString()
        } catch (e: IOException) {
            path.toString()
        }

    fun exists(): Boolean = fs.exists(path)

    fun isDirectory(): Boolean = fs.metadataOrNull(path)?.isDirectory == true
    fun isFile(): Boolean = fs.metadataOrNull(path)?.isRegularFile == true

    fun readText(): String {
        return fs.read(path) { readUtf8() }
    }

    fun writeText(text: String) {
        fs.write(path) { writeUtf8(text) }
    }

    fun appendText(text: String) {
        fs.appendingSink(path).buffer().use { it.writeUtf8(text) }
    }

    fun createNewFile(): Boolean {
        if (exists()) return false
        try {
            writeText("") // Leere Datei erstellen
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun mkdirs(): Boolean {
        return try {
            fs.createDirectories(path)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun delete(): Boolean {
        return try {
            fs.delete(path)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun copy(destDir: OkioFile): Boolean {
        if (!exists() || isDirectory()) return false
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = OkioFile(destDir, name)
        return try {
            fs.copy(path, destFile.path)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun move(destDir: OkioFile): Boolean {
        if (!exists()) return false
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = OkioFile(destDir, name)
        return try {
            fs.atomicMove(path, destFile.path)
            true
        } catch (e: IOException) {
            if (copy(destDir)) {
                delete()
            } else {
                false
            }
        }
    }

    fun renameTo(newName: String): Boolean {
        if (!exists()) return false
        val targetPath = path.parent?.let { it / newName } ?: newName.toPath()
        return try {
            fs.atomicMove(path, targetPath)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun listFiles(): List<OkioFile> {
        return fs.listOrNull(path)?.map { OkioFile(it.toString()) } ?: emptyList()
    }

    operator fun div(child: String): OkioFile = OkioFile((path / child).toString())

    override fun toString() = absolutePath
}
