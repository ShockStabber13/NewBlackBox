package top.niunaijun.blackboxa.util

import org.json.JSONObject
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.biz.cache.WorkspacePrefs
import java.io.File

object WorkspaceUtil {
    private fun bbBaseDir(): File {
        val ctx = App.getContext()
        return File(ctx.getExternalFilesDir(null), "bbcmd")
    }

    fun setWorkspacePath(path: String) {
        WorkspacePrefs.workspacePath = path
        exportWorkspaceJson(path)
        ensureSubfolders(path)
    }

    fun getWorkspacePath(): String = WorkspacePrefs.workspacePath

    fun exportWorkspaceJson(path: String) {
        try {
            val base = bbBaseDir()
            base.mkdirs()
            val obj = JSONObject().apply {
                put("schema", 1)
                put("workspacePath", path)
                put("instances", "instances")
                put("backups", "backups")
                put("installs", "installs")
                put("exports", "exports")
            }
            File(base, "workspace.json").writeText(obj.toString(2))
        } catch (_: Throwable) {
        }
    }

    fun ensureSubfolders(path: String) {
        try {
            val root = File(path)
            if (!root.exists()) root.mkdirs()
            File(root, "instances").mkdirs()
            File(root, "backups").mkdirs()
            File(root, "installs").mkdirs()
            File(root, "exports").mkdirs()
        } catch (_: Throwable) {
        }
    }
}
