@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Just an experimental wrapper around native file system functions.
 * Shouldn't be used. Generated completely with AI. So... do not trust it.
 */
class NativeFile(val path: String) {

    constructor(parent: NativeFile, child: String) : this(parent.path.removeSuffix("/") + "/" + child)

    val name: String get() = path.substringAfterLast('/').ifEmpty { path }

    val parent: NativeFile?
        get() {
            val trimmedPath = path.removeSuffix("/")
            val index = trimmedPath.lastIndexOf('/')
            if (index < 0) return null
            val parentPath = if (index == 0) "/" else trimmedPath.substring(0, index)
            return NativeFile(parentPath)
        }

    val absolutePath: String
        get() = memScoped {
            val realPathPtr = realpath(path, null)
            if (realPathPtr != null) {
                val result = realPathPtr.toKString()
                free(realPathPtr)
                result
            } else {
                // If realpath fails (e.g. file does not exist),
                // we try to return the path at least.
                path
            }
        }

    fun exists(): Boolean = access(path, F_OK) == 0

    fun isDirectory(): Boolean = memScoped {
        val st = alloc<stat>()
        if (stat(path, st.ptr) == 0) {
            return (st.st_mode.toInt() and S_IFMT) == S_IFDIR
        }
        false
    }

    fun isFile(): Boolean = memScoped {
        val st = alloc<stat>()
        if (stat(path, st.ptr) == 0) {
            return (st.st_mode.toInt() and S_IFMT) == S_IFREG
        }
        false
    }

    fun readText(): String {
        val file = fopen(path, "r") ?: throw RuntimeException("Cannot open file for reading: $path")
        try {
            fseek(file, 0.toLong(), SEEK_END)
            val size = ftell(file)
            fseek(file, 0.toLong(), SEEK_SET)

            if (size == 0L) return ""

            val buffer = ByteArray(size.toInt())
            buffer.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.toULong(), size.toULong(), file)
            }
            return buffer.decodeToString()
        } finally {
            fclose(file)
        }
    }

    fun writeText(text: String) {
        val file = fopen(path, "w") ?: throw RuntimeException("Cannot open file for writing: $path")
        try {
            if (text.isNotEmpty()) {
                fputs(text, file)
            }
        } finally {
            fclose(file)
        }
    }

    fun appendText(text: String) {
        val file = fopen(path, "a") ?: throw RuntimeException("Cannot open file for appending: $path")
        try {
            if (text.isNotEmpty()) {
                fputs(text, file)
            }
        } finally {
            fclose(file)
        }
    }

    fun createNewFile(): Boolean {
        if (exists()) return false
        return try {
            writeText("")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun mkdirs(): Boolean {
        if (exists()) return isDirectory()

        val p = parent
        if (p != null && !p.exists()) {
            if (!p.mkdirs()) return false
        }

        // S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH -> 0775 or similar
        // We use 0777 (S_IRWXU | S_IRWXG | S_IRWXO)
        return mkdir(path, (S_IRWXU or S_IRWXG or S_IRWXO).toUInt()) == 0
    }

    fun delete(): Boolean {
        if (!exists()) return false
        return remove(path) == 0
    }

    fun copy(destDir: NativeFile): Boolean {
        if (!exists() || isDirectory()) return false
        if (!destDir.exists()) destDir.mkdirs()
        if (!destDir.isDirectory()) return false

        val destFile = NativeFile(destDir, name)
        val source = fopen(path, "rb") ?: return false
        val dest = fopen(destFile.path, "wb") ?: run {
            fclose(source)
            return false
        }

        val bufferSize = 64 * 1024
        val buffer = ByteArray(bufferSize)
        var success = true

        try {
            buffer.usePinned { pinned ->
                while (true) {
                    val read = fread(pinned.addressOf(0), 1.toULong(), bufferSize.toULong(), source)
                    if (read == 0.toULong()) break
                    val written = fwrite(pinned.addressOf(0), 1.toULong(), read, dest)
                    if (written != read) {
                        success = false
                        break
                    }
                }
            }
        } catch (e: Exception) {
            success = false
        } finally {
            fclose(source)
            fclose(dest)
        }
        return success
    }

    fun move(destDir: NativeFile): Boolean {
        if (!exists()) return false
        if (!destDir.exists()) destDir.mkdirs()
        if (!destDir.isDirectory()) return false

        val destFile = NativeFile(destDir, name)
        if (rename(path, destFile.path) == 0) return true

        if (copy(destDir)) {
            return delete()
        }
        return false
    }

    fun renameTo(newName: String): Boolean {
        if (!exists()) return false
        val p = parent
        val newPath = if (p != null) {
            NativeFile(p, newName).path
        } else {
            newName
        }
        return rename(path, newPath) == 0
    }

    fun listFiles(): List<NativeFile> {
        val dir = opendir(path) ?: return emptyList()
        val result = mutableListOf<NativeFile>()
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name != "." && name != "..") {
                    val separator = if (path.endsWith("/")) "" else "/"
                    result.add(NativeFile("$path$separator$name"))
                }
            }
        } finally {
            closedir(dir)
        }
        return result
    }

    operator fun div(child: String): NativeFile = NativeFile(path.removeSuffix("/") + "/" + child)

    override fun toString() = absolutePath
}
