package top.niunaijun.blackboxa.biz.cache

import top.niunaijun.blackboxa.app.App

/**
 * Workspace settings persisted in SharedPreferences.
 *
 * NOTE: This contains BOTH the old SAF-based fields (treeUri/displayName)
 * and the new raw-path field (workspacePath) so mixed code from different
 * patches still compiles. You can later migrate fully to workspacePath.
 */
object WorkspacePrefs {
    private const val PREF_NAME = "bbcmd_workspace"

    private const val KEY_WORKSPACE_TREE_URI = "workspaceTreeUri"
    private const val KEY_WORKSPACE_DISPLAY_NAME = "workspaceDisplayName"
    private const val KEY_WORKSPACE_PATH = "workspacePath"

    private val sp by lazy {
        App.getContext().getSharedPreferences(PREF_NAME, 0)
    }

    var workspaceTreeUri: String
        get() = sp.getString(KEY_WORKSPACE_TREE_URI, "") ?: ""
        set(value) { sp.edit().putString(KEY_WORKSPACE_TREE_URI, value ?: "").apply() }

    var workspaceDisplayName: String
        get() = sp.getString(KEY_WORKSPACE_DISPLAY_NAME, "") ?: ""
        set(value) { sp.edit().putString(KEY_WORKSPACE_DISPLAY_NAME, value ?: "").apply() }

    var workspacePath: String
        get() = sp.getString(KEY_WORKSPACE_PATH, "") ?: ""
        set(value) { sp.edit().putString(KEY_WORKSPACE_PATH, value ?: "").apply() }
}
