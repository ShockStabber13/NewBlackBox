package top.niunaijun.blackbox.fake.frameworks;

import android.content.Context;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.os.IBStorageManagerService;

/**
 * BStorageManager facade.
 *
 * Option A (recommended here): don't IPC these specific helpers.
 * We generate results in-process using public framework APIs so callers
 * don't depend on hidden/unsupported binder signatures.
 */
public class BStorageManager extends BlackManager<IBStorageManagerService> {
    private static final BStorageManager sStorageManager = new BStorageManager();

    public static BStorageManager get() {
        return sStorageManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.STORAGE_MANAGER;
    }

    /**
     * Android 12+ call-sites may include userId. We intentionally ignore uid/package/flags/userId and
     * return the host-visible volumes via public StorageManager APIs.
     */
    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) {
        try {
            Context ctx = BlackBoxCore.get().getContext();
            if (ctx != null) {
                StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    List<StorageVolume> vols = sm.getStorageVolumes();
                    return vols.toArray(new StorageVolume[0]);
                }
            }
        } catch (Throwable ignored) {
        }
        return new StorageVolume[0];
    }

    /**
     * Return a grantable content:// Uri for a local file using the host app's FileProvider.
     * Fallback to file:// if anything goes wrong (some callers may still accept it).
     */
    public static Uri getUriForFile(String file) {
        try {
            Context ctx = BlackBoxCore.get().getContext();
            if (ctx == null) return Uri.fromFile(new File(file));
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", new File(file));
        } catch (Throwable ignored) {
            return Uri.fromFile(new File(file));
        }
    }
}
