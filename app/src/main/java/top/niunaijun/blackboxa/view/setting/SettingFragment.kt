package top.niunaijun.blackboxa.view.setting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.gms.GmsManagerActivity
import top.niunaijun.blackboxa.view.workspace.WorkspacePickerActivity
import top.niunaijun.blackboxa.view.instance.InstanceBackupActivity
import top.niunaijun.blackboxa.view.instance.InstanceRestoreActivity
import top.niunaijun.blackboxa.view.xp.XpActivity

/**
 *
 * @Description:
 * @Author: wukaicheng
 * @CreateDate: 2021/5/6 22:13
 */
class SettingFragment : PreferenceFragmentCompat() {

    private lateinit var xpEnable: SwitchPreferenceCompat

    private lateinit var xpModule: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        findPreference<Preference>("backup_instance")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), InstanceBackupActivity::class.java))
            true
        }

        findPreference<Preference>("restore_instance")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), InstanceRestoreActivity::class.java))
            true
        }
        xpEnable = findPreference("xp_enable")!!
        xpEnable.isChecked = BlackBoxCore.get().isXPEnable

        xpEnable.setOnPreferenceChangeListener { _, newValue ->
            BlackBoxCore.get().isXPEnable = (newValue == true)
            true
        }
        //xp模块跳转
        xpModule = findPreference("xp_module")!!
        xpModule.setOnPreferenceClickListener {
            val intent = Intent(requireActivity(), XpActivity::class.java)
            requireContext().startActivity(intent)
            true
        }
        initGms()

        // Share host /sdcard with virtual apps
        invalidHideState {
            val shareSdcardPref: Preference = (findPreference("share_host_sdcard")!!)
            val share = AppManager.mBlackBoxLoader.shareHostSdcard()
            shareSdcardPref.setDefaultValue(share)
            shareSdcardPref
        }

        // Quick shortcut to the "All files access" settings page for this host app
        val grantAllFiles: Preference = (findPreference("grant_all_files_access")!!)
        grantAllFiles.setOnPreferenceClickListener {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback for OEM variants
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
            true
        }

        // Workspace folder picker (custom, no SAF)
        val workspaceFolder: Preference = (findPreference("workspace_folder")!!)
        workspaceFolder.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), WorkspacePickerActivity::class.java))
            true
        }
        // Backup instance
        val backupInstance: Preference = (findPreference("backup_instance")!!)
        backupInstance.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), InstanceBackupActivity::class.java))
            true
        }

        // Restore instance
        val restoreInstance: Preference = (findPreference("restore_instance")!!)
        restoreInstance.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), InstanceRestoreActivity::class.java))
            true
        }


        invalidHideState{
            val xpHidePreference: Preference = (findPreference("xp_hide")!!)
            val hideXposed = AppManager.mBlackBoxLoader.hideXposed()
            xpHidePreference.setDefaultValue(hideXposed)
            xpHidePreference
        }

        invalidHideState{
            val rootHidePreference: Preference = (findPreference("root_hide")!!)
            val hideRoot = AppManager.mBlackBoxLoader.hideRoot()
            rootHidePreference.setDefaultValue(hideRoot)
            rootHidePreference
        }

        invalidHideState {
            val daemonPreference: Preference = (findPreference("daemon_enable")!!)
            val mDaemonEnable = AppManager.mBlackBoxLoader.daemonEnable()
            daemonPreference.setDefaultValue(mDaemonEnable)
            daemonPreference
        }

    }

    private fun initGms() {
        val gmsManagerPreference: Preference = (findPreference("gms_manager")!!)

        if (BlackBoxCore.get().isSupportGms) {

            gmsManagerPreference.setOnPreferenceClickListener {
                GmsManagerActivity.start(requireContext())
                true
            }
        } else {
            gmsManagerPreference.summary = getString(R.string.no_gms)
            gmsManagerPreference.isEnabled = false
        }
    }

    private fun invalidHideState(block: () -> Preference) {
        val pref = block()
        pref.setOnPreferenceChangeListener { preference, newValue ->
            val tmpHide = (newValue == true)
            when (preference.key) {
                "share_host_sdcard" -> {
                    AppManager.mBlackBoxLoader.invalidShareHostSdcard(tmpHide)
                }

                "xp_hide" -> {
                    AppManager.mBlackBoxLoader.invalidHideXposed(tmpHide)
                }

                "root_hide" -> {

                    AppManager.mBlackBoxLoader.invalidHideRoot(tmpHide)
                }

                "daemon_enable" -> {
                    AppManager.mBlackBoxLoader.invalidDaemonEnable(tmpHide)
                }
            }

            toast(R.string.restart_module)
            return@setOnPreferenceChangeListener true
        }
    }
}