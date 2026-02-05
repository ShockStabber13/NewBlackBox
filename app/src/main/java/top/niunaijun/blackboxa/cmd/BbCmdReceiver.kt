package top.niunaijun.blackboxa.cmd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import java.io.*
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 * Simple ADB-friendly broadcast entrypoints for BlackBox operations.
 *
 * Example:
 *  adb shell am broadcast -a top.niunaijun.blackboxa.action.BACKUP --es pkg com.whatsapp --ei user 0
 */
class BbCmdReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val res = handle(context, intent)
                setResult(RESULT_OK, res.first, res.second)
            } catch (t: Throwable) {
                Log.e(TAG, "BbCmdReceiver failed", t)
                val b = Bundle().apply { putString("error", t.toString()) }
                setResult(RESULT_ERROR, "error", b)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context, intent: Intent): Pair<String, Bundle> {
        val action = intent.action ?: ""
        val pkg = intent.getStringExtra(EXTRA_PKG)?.trim().orEmpty()
        val userId = intent.getIntExtra(EXTRA_USER, 0)

        val core = BlackBoxCore.get()

        return when (action) {
            ACTION_BACKUP -> {
                if (pkg.isBlank()) {
                    return "missing_pkg" to Bundle().apply { putString("hint", "Provide --es pkg <packageName>") }
                }
                core.stopPackage(pkg, userId)
                val explicit = intent.getStringExtra(EXTRA_BACKUP_PATH)
                val out = backupToExternal(context, pkg, userId, explicit)
                "ok" to Bundle().apply {
                    putString("backup", out.absolutePath)
                    putBoolean("is_dir", out.isDirectory)
                    putString("format", detectFormat(out))
                }
            }

            ACTION_RESTORE -> {
                if (pkg.isBlank()) {
                    return "missing_pkg" to Bundle().apply { putString("hint", "Provide --es pkg <packageName>") }
                }
                core.stopPackage(pkg, userId)
                core.clearPackage(pkg, userId) // clears virtual app data before restore
                val src = resolveBackupSource(context, pkg, userId, intent.getStringExtra(EXTRA_BACKUP_PATH))
                restoreFromExternal(context, pkg, userId, src)
                "ok" to Bundle().apply {
                    putString("restored_from", src.absolutePath)
                    putBoolean("is_dir", src.isDirectory)
                    putString("format", detectFormat(src))
                }
            }

            ACTION_CLEAR -> {
                if (pkg.isBlank()) {
                    return "missing_pkg" to Bundle().apply { putString("hint", "Provide --es pkg <packageName>") }
                }
                core.stopPackage(pkg, userId)
                core.clearPackage(pkg, userId)
                "ok" to Bundle().apply { putString("cleared", "$pkg u$userId") }
            }

            ACTION_UNINSTALL -> {
                if (pkg.isBlank()) {
                    return "missing_pkg" to Bundle().apply { putString("hint", "Provide --es pkg <packageName>") }
                }
                core.stopPackage(pkg, userId)
                // In this project version uninstallPackageAsUser returns Unit (void).
                // If it throws, the receiver's outer try/catch will return an error bundle.
                core.uninstallPackageAsUser(pkg, userId)
                "ok" to Bundle().apply { putBoolean("success", true) }
            }

            ACTION_INSTALL -> {
                val apkPath = intent.getStringExtra(EXTRA_APK_PATH)?.trim().orEmpty()
                if (apkPath.isBlank()) {
                    return "missing_apk" to Bundle().apply {
                        putString("hint", "Provide --es apk /sdcard/.../base.apk (single APK) OR /sdcard/.../<folder> (base.apk + split_*.apk)")
                    }
                }
                // In this project version installPackageAsUser returns InstallResult.
                val installResult = if (
                    apkPath.startsWith("content:") ||
                    apkPath.startsWith("http://") ||
                    apkPath.startsWith("https://")
                ) {
                    core.installPackageAsUser(Uri.parse(apkPath), userId)
                } else {
                    // Accept a single APK path OR a folder containing base.apk + split_*.apk.
                    core.installPackageAsUser(File(apkPath), userId)
                }

                "ok" to Bundle().apply {
                    putBoolean("success", installResult.success)
                    putString("packageName", installResult.packageName)
                    putString("msg", installResult.msg)
                }
            }

            
            ACTION_EXTRACT -> {
                if (pkg.isBlank()) {
                    return "missing_pkg" to Bundle().apply { putString("hint", "Provide --es pkg <packageName>") }
                }
                val explicit = intent.getStringExtra(EXTRA_OUT)
                val out = extractApkToExternal(context, pkg, userId, explicit)
                "ok" to Bundle().apply {
                    putString("apk", out.absolutePath)
                }
            }

else -> {
                "unknown_action" to Bundle().apply { putString("action", action) }
            }
        }
    }

    private fun baseDir(context: Context): File {
        // /sdcard/Android/data/<pkg>/files/bbcmd
        val root = context.getExternalFilesDir("bbcmd") ?: File(context.filesDir, "bbcmd")
        if (!root.exists()) root.mkdirs()
        return root
    }

    private fun backupsDir(context: Context): File {
        val d = File(baseDir(context), "backups")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun exportsDir(context: Context): File {
        val d = File(baseDir(context), "exports")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /**
     * Export the virtual app APK(s) to external storage.
     *
     * - If [explicitPath] ends with .apk: export base.apk to that file.
     * - Otherwise export a folder containing base.apk and any split_*.apk.
     */
    private fun extractApkToExternal(context: Context, pkg: String, userId: Int, explicitPath: String?): File {
        val baseApk = top.niunaijun.blackbox.core.env.BEnvironment.getBaseApkDir(pkg)
        if (!baseApk.exists()) throw FileNotFoundException("base.apk not found: ${baseApk.absolutePath}")

        val out = if (!explicitPath.isNullOrBlank()) {
            File(explicitPath)
        } else {
            File(exportsDir(context), "${pkg}-u${userId}")
        }

        // Export base only
        if (out.name.endsWith(".apk", ignoreCase = true)) {
            out.parentFile?.mkdirs()
            FileInputStream(baseApk).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            return out
        }

        // Export folder: base + splits
        if (out.exists()) out.deleteRecursively()
        out.mkdirs()
        val baseOut = File(out, "base.apk")
        FileInputStream(baseApk).use { input ->
            FileOutputStream(baseOut).use { output -> input.copyTo(output) }
        }

        val apkDir = baseApk.parentFile
        apkDir?.listFiles { f -> f.isFile && f.name.startsWith("split_") && f.name.endsWith(".apk", ignoreCase = true) }
            ?.forEach { splitFile ->
                val target = File(out, splitFile.name)
                FileInputStream(splitFile).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }

        return out
    }

    private fun resolveBackupZip(context: Context, pkg: String, userId: Int, explicit: String?): File {
        if (!explicit.isNullOrBlank()) return File(explicit)
        return File(backupsDir(context), "${pkg}-u${userId}.zip")
    }

    private fun defaultBackupZip(context: Context, pkg: String, userId: Int): File {
        return File(backupsDir(context), "${pkg}-u${userId}.zip")
    }

    /**
     * Back up the virtual app data (its BEnvironment data dir) to either:
     *  - a .zip file (default)
     *  - a .tar.gz/.tgz file
     *  - a directory (folder-to-folder backup)
     *
     * Format is selected by the [explicitPath] file extension:
     *  - *.zip     => ZIP
     *  - *.tar.gz  => TAR+GZIP
     *  - *.tgz     => TAR+GZIP
     *  - otherwise => DIRECTORY
     */
    private fun backupToExternal(context: Context, pkg: String, userId: Int, explicitPath: String?): File {
        val dataDir = top.niunaijun.blackbox.core.env.BEnvironment.getDataDir(pkg, userId)
        val target = resolveBackupTarget(context, pkg, userId, explicitPath)
        when (formatOf(target)) {
            BackupFormat.DIR -> {
                if (target.exists()) target.deleteRecursively()
                target.mkdirs()
                copyDirContents(dataDir, target)
            }
            BackupFormat.ZIP -> {
                if (target.exists()) target.delete()
                target.parentFile?.mkdirs()
                zipDir(dataDir, target)
            }
            BackupFormat.TAR_GZ -> {
                if (target.exists()) target.delete()
                target.parentFile?.mkdirs()
                tarGzDir(dataDir, target)
            }
        }
        return target
    }

    /** Restore virtual app data from a .zip, .tar.gz/.tgz, or directory. */
    private fun restoreFromExternal(context: Context, pkg: String, userId: Int, source: File) {
        val dataDir = top.niunaijun.blackbox.core.env.BEnvironment.getDataDir(pkg, userId)

        // Ensure clean directory (clearPackage should have done it, but be safe)
        if (dataDir.exists()) dataDir.deleteRecursively()
        dataDir.mkdirs()

        when {
            source.isDirectory -> copyDirContents(source, dataDir)
            isTarGz(source) -> untarGz(source, dataDir)
            source.name.endsWith(".zip", ignoreCase = true) -> unzip(source, dataDir)
            else -> throw IllegalArgumentException("Unsupported backup type (expected .zip, .tar.gz/.tgz, or folder): ${source.absolutePath}")
        }
    }

    private fun resolveBackupTarget(context: Context, pkg: String, userId: Int, explicitPath: String?): File {
        val p = explicitPath?.trim().orEmpty()
        if (p.isEmpty()) return defaultBackupZip(context, pkg, userId)
        return File(p)
    }

    private fun resolveBackupSource(context: Context, pkg: String, userId: Int, explicitPath: String?): File {
        val p = explicitPath?.trim().orEmpty()
        val f = if (p.isEmpty()) defaultBackupZip(context, pkg, userId) else File(p)
        if (!f.exists()) throw FileNotFoundException("Backup not found: ${f.absolutePath}")
        return f
    }

    private enum class BackupFormat { DIR, ZIP, TAR_GZ }

    private fun isTarGz(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".tar.gz") || n.endsWith(".tgz")
    }

    private fun formatOf(f: File): BackupFormat {
        val n = f.name.lowercase()
        return when {
            isTarGz(f) -> BackupFormat.TAR_GZ
            n.endsWith(".zip") -> BackupFormat.ZIP
            else -> BackupFormat.DIR
        }
    }

    private fun detectFormat(f: File): String {
        return when (formatOf(f)) {
            BackupFormat.DIR -> "dir"
            BackupFormat.ZIP -> "zip"
            BackupFormat.TAR_GZ -> "tar.gz"
        }
    }

    /** Copy the contents of [srcDir] into [dstDir] (folder -> folder), recursively. */
    private fun copyDirContents(srcDir: File, dstDir: File) {
        if (!srcDir.exists()) throw FileNotFoundException("Source dir not found: ${srcDir.absolutePath}")
        if (!srcDir.isDirectory) throw IllegalArgumentException("Source is not a directory: ${srcDir.absolutePath}")
        srcDir.walkTopDown().forEach { f ->
            val rel = f.relativeTo(srcDir).path
            if (rel.isEmpty()) return@forEach
            val out = File(dstDir, rel)
            if (f.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                FileInputStream(f).use { input ->
                    FileOutputStream(out).use { output ->
                        input.copyTo(output)
                    }
                }
                out.setLastModified(f.lastModified())
            }
        }
    }

    private fun zipDir(srcDir: File, outZip: File) {
        if (!srcDir.exists()) throw FileNotFoundException("Virtual data dir not found: ${srcDir.absolutePath}")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outZip))).use { zos ->
            val basePath = srcDir.absolutePath.trimEnd(File.separatorChar)
            srcDir.walkTopDown().forEach { f ->
                val abs = f.absolutePath
                val rel = abs.substring(basePath.length).trimStart(File.separatorChar)
                if (rel.isEmpty()) return@forEach
                val entryName = rel.replace(File.separatorChar, '/')
                if (f.isDirectory) {
                    val e = ZipEntry(if (entryName.endsWith("/")) entryName else "$entryName/")
                    zos.putNextEntry(e)
                    zos.closeEntry()
                } else {
                    val e = ZipEntry(entryName)
                    zos.putNextEntry(e)
                    FileInputStream(f).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun tarGzDir(srcDir: File, outTarGz: File) {
        if (!srcDir.exists()) throw FileNotFoundException("Virtual data dir not found: ${srcDir.absolutePath}")

        BufferedOutputStream(FileOutputStream(outTarGz)).use { fos ->
            GzipCompressorOutputStream(fos).use { gzos ->
                TarArchiveOutputStream(gzos).use { tos ->
                    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                    val basePath = srcDir.absolutePath.trimEnd(File.separatorChar)
                    srcDir.walkTopDown().forEach { f ->
                        val abs = f.absolutePath
                        val rel = abs.substring(basePath.length).trimStart(File.separatorChar)
                        if (rel.isEmpty()) return@forEach

                        val entryName = rel.replace(File.separatorChar, '/')
                        val entry = if (f.isDirectory) {
                            TarArchiveEntry("${entryName.trimEnd('/')}/")
                        } else {
                            TarArchiveEntry(f, entryName)
                        }

						// TarArchiveEntry.modTime expects a Date (not a millis Long)
						entry.modTime = Date(f.lastModified())
                        tos.putArchiveEntry(entry)
                        if (f.isFile) {
                            FileInputStream(f).use { it.copyTo(tos) }
                        }
                        tos.closeArchiveEntry()
                    }
                    tos.finish()
                }
            }
        }
    }

    private fun untarGz(tarGzFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()

        val destPath = destDir.canonicalPath
        BufferedInputStream(FileInputStream(tarGzFile)).use { fis ->
            GzipCompressorInputStream(fis).use { gzis ->
                TarArchiveInputStream(gzis).use { tis ->
                    var entry = tis.nextTarEntry
                    while (entry != null) {
                        val name = entry.name
                        val outFile = File(destDir, name)
                        val outPath = outFile.canonicalPath
                        if (!outPath.startsWith(destPath + File.separator)) {
                            throw SecurityException("Blocked tar path traversal: $name")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                tis.copyTo(fos)
                            }
                            // best-effort timestamps
                            runCatching { outFile.setLastModified(entry.modTime.time) }
                        }

                        entry = tis.nextTarEntry
                    }
                }
            }
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                // avoid Zip Slip
                val outFile = File(destDir, name)
                val destPath = destDir.canonicalPath
                val outPath = outFile.canonicalPath
                if (!outPath.startsWith(destPath + File.separator)) {
                    throw SecurityException("Blocked zip path traversal: $name")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    companion object {
        private const val TAG = "BbCmdReceiver"

        const val EXTRA_PKG = "pkg"
        const val EXTRA_USER = "user"
        const val EXTRA_APK_PATH = "apk"
        const val EXTRA_BACKUP_PATH = "backup"
        const val EXTRA_OUT = "out"

        const val ACTION_BACKUP = "top.niunaijun.blackboxa.action.BACKUP"
        const val ACTION_RESTORE = "top.niunaijun.blackboxa.action.RESTORE"
        const val ACTION_CLEAR = "top.niunaijun.blackboxa.action.CLEAR"
        const val ACTION_UNINSTALL = "top.niunaijun.blackboxa.action.UNINSTALL"
        const val ACTION_INSTALL = "top.niunaijun.blackboxa.action.INSTALL"
        const val ACTION_EXTRACT = "top.niunaijun.blackboxa.action.EXTRACT"

        private const val RESULT_OK = 0
        private const val RESULT_ERROR = 1
    }
}
