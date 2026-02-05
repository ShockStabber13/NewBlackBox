
package top.niunaijun.blackboxa.util

import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object InstanceZipUtil {
    private val ROOT_ITEMS = listOf("blackbox", "shared_prefs", "databases", "files")
    private const val META_NAME = "instance_meta.json"
    private const val MAGIC = "blackbox_instance_zip_v1"

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
        }
    }

    fun backupInstance(baseDir: File, outZip: File, instanceName: String) {
        if (!baseDir.exists()) throw FileNotFoundException("Base dir missing: ${baseDir.absolutePath}")
        outZip.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outZip))).use { zos ->
            val meta = JSONObject().apply {
                put("magic", MAGIC)
                put("name", instanceName)
                put("schema", 1)
            }.toString(2)
            zos.putNextEntry(ZipEntry(META_NAME))
            zos.write(meta.toByteArray())
            zos.closeEntry()

            for (item in ROOT_ITEMS) {
                val src = File(baseDir, item)
                if (src.exists()) {
                    zipRecursively(src, item, zos)
                }
            }
        }
    }

    private fun zipRecursively(file: File, entryPath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val dirEntry = if (entryPath.endsWith("/")) entryPath else "$entryPath/"
            zos.putNextEntry(ZipEntry(dirEntry))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                zipRecursively(child, "$entryPath/${child.name}", zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { fis -> copyStream(fis, zos) }
            zos.closeEntry()
        }
    }

    fun isCompatible(zipFile: File): Boolean {
        if (!zipFile.exists()) return false
        return try {
            ZipFile(zipFile).use { zf ->
                val meta = zf.getEntry(META_NAME)
                if (meta != null) {
                    val text = zf.getInputStream(meta).bufferedReader().use { it.readText() }
                    val obj = JSONObject(text)
                    return obj.optString("magic") == MAGIC
                }
                // legacy fallback
                zf.getEntry("blackbox/") != null || zf.entries().asSequence().any { it.name.startsWith("blackbox/") }
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getDisplayName(zipFile: File): String {
        try {
            ZipFile(zipFile).use { zf ->
                val meta = zf.getEntry(META_NAME)
                if (meta != null) {
                    val text = zf.getInputStream(meta).bufferedReader().use { it.readText() }
                    val obj = JSONObject(text)
                    val n = obj.optString("name")
                    if (n.isNotBlank()) return n
                }
            }
        } catch (_: Throwable) {
        }
        return zipFile.name.removeSuffix(".zip")
    }

    fun restoreInstance(zipFile: File, baseDir: File) {
        if (!isCompatible(zipFile)) throw IllegalArgumentException("Incompatible instance zip")
        baseDir.mkdirs()

        // clean target roots
        ROOT_ITEMS.forEach { item ->
            val f = File(baseDir, item)
            if (f.exists()) f.deleteRecursively()
        }

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name
                if (name == META_NAME) {
                    zis.closeEntry()
                    continue
                }
                // only allow known roots
                if (ROOT_ITEMS.none { name == it || name.startsWith("$it/") }) {
                    zis.closeEntry()
                    continue
                }
                val outFile = File(baseDir, name)
                val canonicalBase = baseDir.canonicalPath + File.separator
                val canonicalOut = outFile.canonicalPath
                if (!canonicalOut.startsWith(canonicalBase)) {
                    zis.closeEntry()
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> copyStream(zis, fos) }
                }
                zis.closeEntry()
            }
        }
    }
}
