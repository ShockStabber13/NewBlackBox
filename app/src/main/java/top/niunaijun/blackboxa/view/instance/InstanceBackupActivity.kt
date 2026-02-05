package top.niunaijun.blackboxa.view.instance

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import top.niunaijun.blackboxa.R
import java.util.concurrent.Executors

class InstanceBackupActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var btnBackup: Button
    private lateinit var tvStatus: TextView
    private lateinit var progress: ProgressBar

    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instance_backup)

        etName = findViewById(R.id.etBackupName)
        btnBackup = findViewById(R.id.btnDoBackup)
        tvStatus = findViewById(R.id.tvBackupStatus)
        progress = findViewById(R.id.pbBackup)

        btnBackup.setOnClickListener {
            val raw = etName.text?.toString()?.trim().orEmpty()
            if (raw.isEmpty()) {
                etName.error = getString(R.string.instance_backup_name_required)
                return@setOnClickListener
            }

            val safeName = sanitizeFileName(raw)
            startBackup(safeName)
        }
    }
    private fun startBackup(safeName: String) {
        setBusy(true, getString(R.string.instance_backup_in_progress))

        io.execute {
            try {
                val outZip = top.niunaijun.blackboxa.util.InstanceBackupUtil.backupNow(this, safeName)

                runOnUiThread {
                    setBusy(false, getString(R.string.instance_backup_done))
                    Toast.makeText(
                        this,
                        getString(R.string.instance_backup_done_path, outZip.absolutePath),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    setBusy(false, "")
                    Toast.makeText(
                        this,
                        getString(R.string.instance_backup_failed, (t.message ?: "unknown error")),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    private fun setBusy(busy: Boolean, status: String) {
        btnBackup.isEnabled = !busy
        etName.isEnabled = !busy
        progress.visibility = if (busy) ProgressBar.VISIBLE else ProgressBar.GONE
        tvStatus.text = status
    }

    private fun sanitizeFileName(input: String): String {
        return input
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { "InstanceBackup" }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }
}
