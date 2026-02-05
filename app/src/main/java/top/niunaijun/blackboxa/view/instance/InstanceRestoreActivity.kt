package top.niunaijun.blackboxa.view.instance

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.util.InstanceRestoreUtil
import top.niunaijun.blackboxa.util.WorkspaceUtil
import java.io.File

class InstanceRestoreActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var listView: ListView
    private lateinit var btnRestore: Button

    private var selectedIndex = -1
    private var restoreFiles: List<File> = emptyList()

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instance_restore)

        tvTitle = findViewById(R.id.tv_title)
        listView = findViewById(R.id.list_instances)
        btnRestore = findViewById(R.id.btn_restore)

        tvTitle.text = "Choose Instance Restore"

        loadRestoreList()

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
        }

        btnRestore.setOnClickListener {
            if (selectedIndex !in restoreFiles.indices) {
                Toast.makeText(this, "Select a backup first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val zip = restoreFiles[selectedIndex]
            try {
                InstanceRestoreUtil.restoreFromZip(this, zip)
                Toast.makeText(this, "Restore complete. Restarting app…", Toast.LENGTH_SHORT).show()
                restartApp()
            } catch (t: Throwable) {
                Toast.makeText(this, "Restore failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } ?: return

        startActivity(intent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }
    private fun loadRestoreList() {
        val workspace = WorkspaceUtil.getWorkspacePath()
        if (workspace.isBlank()) {
            Toast.makeText(this, "Workspace not set", Toast.LENGTH_LONG).show()
            restoreFiles = emptyList()
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, emptyList<String>())
            return
        }

        val instancesDir = File(workspace, "instances")
        if (!instancesDir.exists()) instancesDir.mkdirs()

        restoreFiles = instancesDir
            .listFiles { f -> f.isFile && f.name.lowercase().endsWith(".zip") }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

        val names = restoreFiles.map { it.name }
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, names)

        selectedIndex = if (restoreFiles.isNotEmpty()) 0 else -1
        if (selectedIndex == 0) listView.setItemChecked(0, true)
    }
}
