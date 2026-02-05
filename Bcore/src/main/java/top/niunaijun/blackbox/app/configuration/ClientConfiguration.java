package top.niunaijun.blackbox.app.configuration;

import java.io.File;

/**
 * updated by alex5402 on 5/4/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * TFNQw5HgWUS33Ke1eNmSFTwoQySGU7XNsK (USDT TRC20)
 */
public abstract class ClientConfiguration {

    public boolean isHideRoot() {
        return false;
    }

    public boolean isHideXposed() {
        return false;
    }

    /**
     * If true, BlackBox will expose the host's primary external storage ("/sdcard") to virtual apps
     * instead of using the per-user virtual external directory.
     *
     * Note: The host app must have file access permission on the device (e.g. "All files access" on
     * Android 11+) for virtual apps to be able to read/write the shared storage.
     */
    public boolean isShareHostSdcard() {
        return false;
    }

    public abstract String getHostPackageName();

    public boolean isEnableDaemonService() {
        return true;
    }

    public boolean isEnableLauncherActivity() {
        return true;
    }

    /**
     * This method is called when an internal application requests to install a new application.
     *
     * @return Is it handled?
     */
    public boolean requestInstallPackage(File file, int userId) {
        return false;
    }
}
