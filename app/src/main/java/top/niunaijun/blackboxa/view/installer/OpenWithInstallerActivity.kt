package top.niunaijun.blackboxa.view.installer

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import top.niunaijun.blackbox.BlackBoxCore
import java.io.File
import java.io.FileOutputStream
import top.niunaijun.blackboxa.util.GoldLoadingDialog


/**
 * Entry point for:
 *  - Host "Open with..." for APKs (Intent data Uri)
 *  - Guest launcher icon (no data Uri) -> opens a document picker to choose an APK
 */
class OpenWithInstallerActivity : Activity() {
    private lateinit var loading: GoldLoadingDialog

    companion object {
        private const val REQ_PICK_APK = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loading = GoldLoadingDialog(this)


        val uri = extractApkUri(intent)
        if (uri == null) {
            // Launched directly (e.g., inside guest). Let user pick an APK.
            launchPicker()
            return
        }

        handleInstallFromUri(uri)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)

        val uri = extractApkUri(intent)
        if (uri == null) {
            launchPicker()
            return
        }
        handleInstallFromUri(uri)
    }

    private fun launchPicker() {
        try {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.android.package-archive"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(pick, REQ_PICK_APK)
        } catch (t: Throwable) {
            toast("Picker failed: ${t.message ?: t.javaClass.simpleName}")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_APK) return

        if (resultCode != RESULT_OK) {
            finish()
            return
        }

        val uri = data?.data
        if (uri == null) {
            toast("No file selected")
            finish()
            return
        }

        // Persist permission if the picker supports it
        try {
            val takeFlags = (data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Throwable) {
            // Not all providers support persistable permission; ignore.
        }

        handleInstallFromUri(uri)
    }

    private fun handleInstallFromUri(uri: Uri) {
        loading.show("Installing with BlackBox…")
        Thread {
            try {
                contentResolver.openInputStream(uri)?.close()
                val apkFile = when (uri.scheme) {
                    ContentResolver.SCHEME_FILE -> File(uri.path ?: "")
                    else -> copyToCache(uri)
                }

                if (!apkFile.exists() || apkFile.length() <= 0L) {
                    runOnUiThread { toast("APK copy failed") }
                    return@Thread
                }

                val res = BlackBoxCore.get().installPackageAsUser(apkFile, 0)
                val success = readBoolean(res, "isSuccess")
                    ?: readBoolean(res, "success")
                    ?: readInt(res, "result")?.let { it == 0 }
                    ?: false

                runOnUiThread {
                    if (success) toast("Installed")
                    else {
                        val msg = readString(res, "msg")
                            ?: readString(res, "message")
                            ?: readString(res, "error")
                            ?: res.toString()
                        toast("Install failed: $msg")
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread { toast("Install error: ${t.message ?: t.javaClass.simpleName}") }
            } finally {
                runOnUiThread {
                    loading.dismiss()

                    finish()
                }
            }
        }.start()
    }


    private fun copyToCache(uri: Uri): File {
        val out = File(cacheDir, "picked-${System.currentTimeMillis()}.apk")
        contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IllegalStateException("Cannot open input stream")
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    fos.write(buf, 0, r)
                }
                fos.flush()
            }
        }
        return out
    }

    private fun extractApkUri(intent: Intent?): Uri? {
        if (intent == null) return null

        // Prefer data Uri
        intent.data?.let { return it }

        // Some file managers put it in ClipData
        val cd = intent.clipData
        if (cd != null && cd.itemCount > 0) {
            val u = cd.getItemAt(0)?.uri
            if (u != null) return u
        }

        return null
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun readBoolean(obj: Any?, getterOrField: String): Boolean? {
        if (obj == null) return null
        return try {
            val m = obj.javaClass.methods.firstOrNull { it.name == getterOrField && it.parameterTypes.isEmpty() }
            if (m != null) {
                (m.invoke(obj) as? Boolean)
            } else {
                val f = obj.javaClass.fields.firstOrNull { it.name == getterOrField }
                (f?.get(obj) as? Boolean)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readInt(obj: Any?, field: String): Int? {
        if (obj == null) return null
        return try {
            val f = obj.javaClass.fields.firstOrNull { it.name == field }
            when (val v = f?.get(obj)) {
                is Int -> v
                is Number -> v.toInt()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readString(obj: Any?, field: String): String? {
        if (obj == null) return null
        return try {
            val f = obj.javaClass.fields.firstOrNull { it.name == field }
            (f?.get(obj) as? String)
        } catch (_: Throwable) {
            null
        }
    }
}