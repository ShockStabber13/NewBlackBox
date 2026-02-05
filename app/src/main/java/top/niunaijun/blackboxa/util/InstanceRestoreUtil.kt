package top.niunaijun.blackboxa.util

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object InstanceRestoreUtil {
    @RequiresApi(Build.VERSION_CODES.N)
    fun restoreFromZip(context: Context, zipFile: File) {
        require(zipFile.exists()) { "Zip not found: ${zipFile.absolutePath}" }

        // MUST match backup source path exactly:
        val target = File(context.dataDir, "blackbox")

        if (target.exists()) target.deleteRecursively()
        target.mkdirs()

        unzip(zipFile, target)
    }




    private fun unzip(zip: File, outDir: File) {
        val outCanonical = outDir.canonicalPath + File.separator

        ZipInputStream(FileInputStream(zip)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(outDir, entry.name)
                val outPath = outFile.canonicalPath
                require(outPath.startsWith(outCanonical)) {
                    "Invalid zip entry: ${entry!!.name}"
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

}
