package top.niunaijun.blackboxa.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object InstanceBackupUtil {

    /**
     * Change this if your workspace root is stored elsewhere.
     * For now: /storage/emulated/0/BlackBoxWorkspace
     */
    private fun workspaceRoot(context: Context): File {
        val path = top.niunaijun.blackboxa.util.WorkspaceUtil.getWorkspacePath()
        require(path.isNotBlank()) { "Workspace folder not set" }

        val root = File(path)
        require(root.exists() && root.isDirectory) { "Workspace not found: $path" }

        // optional but good:
        top.niunaijun.blackboxa.util.WorkspaceUtil.ensureSubfolders(path)

        return root
    }

    private fun instancesDir(context: Context): File {
        return File(workspaceRoot(context), "instances")
    }

    /**
     * TODO: if your real source instance path differs, change this.
     * Common candidate:
     *   /data/user/0/<host_pkg>/files/blackbox
     */


    fun backupNow(context: Context, backupName: String): File {
        Log.d("NicholasKwekTest", "backupNow ENTER name=$backupName")

        val src = File(context.dataDir, "blackbox")
        Log.d("NicholasKwekTest", "src=${src.absolutePath}, exists=${src.exists()}, isDir=${src.isDirectory}")

        require(src.exists() && src.isDirectory) {
            "Source instance folder not found: ${src.absolutePath}"
        }

        val outDir = instancesDir(context)
        Log.d("NicholasKwekTest", "outDir=${outDir.absolutePath}")
        if (!outDir.exists()) outDir.mkdirs()

        val outZip = uniqueZipFile(outDir, backupName)
        Log.d("NicholasKwekTest", "outZip=${outZip.absolutePath}")

        zipFolder(src, outZip)
        Log.d("NicholasKwekTest", "backupNow DONE")

        return outZip
    }

    private fun uniqueZipFile(dir: File, baseName: String): File {
        var file = File(dir, "$baseName.zip")
        if (!file.exists()) return file

        var i = 1
        while (true) {
            val candidate = File(dir, "${baseName}_$i.zip")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private fun zipFolder(sourceDir: File, outZip: File) {
        FileOutputStream(outZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val rootPath = sourceDir.absolutePath
                zipRec(sourceDir, rootPath, zos)
            }
        }
    }

    private fun zipRec(node: File, rootPath: String, zos: ZipOutputStream) {
        if (node.isDirectory) {
            val children = node.listFiles() ?: emptyArray()
            if (children.isEmpty()) {
                val rel = relativePath(node, rootPath) + "/"
                zos.putNextEntry(ZipEntry(rel))
                zos.closeEntry()
            } else {
                for (child in children) zipRec(child, rootPath, zos)
            }
            return
        }

        val rel = relativePath(node, rootPath)
        zos.putNextEntry(ZipEntry(rel))
        FileInputStream(node).use { fis -> fis.copyTo(zos) }
        zos.closeEntry()
    }

    private fun relativePath(file: File, rootPath: String): String {
        val full = file.absolutePath
        val rel = full.removePrefix(rootPath).removePrefix(File.separator)
        return rel.replace(File.separatorChar, '/')
    }
}
