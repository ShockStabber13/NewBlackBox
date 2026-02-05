package top.niunaijun.blackbox.core;

import android.content.pm.PackageManager;

import java.util.LinkedHashSet;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

public class GmsCore {
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";

    // Keep deterministic order (LinkedHashSet preserves insertion order)
    private static final Set<String> GOOGLE_SERVICE = new LinkedHashSet<>();
    private static final Set<String> GOOGLE_APP = new LinkedHashSet<>();

    static {
        // Core services first
        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");

        // Then apps
        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add("com.google.android.play.games");
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");
    }

    public static boolean isGoogleService(String packageName) {
        return GOOGLE_SERVICE.contains(packageName);
    }

    public static boolean isGoogleAppOrService(String packageName) {
        return GOOGLE_SERVICE.contains(packageName) || GOOGLE_APP.contains(packageName);
    }

    public static InstallResult installGApps(int userId) {
        // Merge in deterministic order
        Set<String> ordered = new LinkedHashSet<>();
        ordered.addAll(GOOGLE_SERVICE);
        ordered.addAll(GOOGLE_APP);

        InstallResult result = installPackages(ordered, userId);
        if (result == null || !result.success) {
            uninstallGApps(userId); // rollback partial install
            if (result == null) {
                InstallResult r = new InstallResult();
                r.success = false;
                r.msg = "install failed: unknown error";
                return r;
            }
            return result;
        }
        return result;
    }

    private static InstallResult installPackages(Set<String> packages, int userId) {
        BlackBoxCore core = BlackBoxCore.get();
        PackageManager pm = BlackBoxCore.getContext().getPackageManager();

        int attempted = 0;
        int installedOrAlready = 0;

        for (String packageName : packages) {
            // already installed in guest
            if (core.isInstalled(packageName, userId)) {
                installedOrAlready++;
                continue;
            }

            // must exist on host for clone/install-by-package
            try {
                pm.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue; // skip missing host pkg
            }

            attempted++;
            InstallResult one = core.installPackageAsUser(packageName, userId);
            if (one == null || !one.success) {
                if (one == null) {
                    InstallResult r = new InstallResult();
                    r.success = false;
                    r.msg = "install returned null for " + packageName;
                    return r;
                }
                if (one.msg == null || one.msg.length() == 0) {
                    one.msg = "install failed for " + packageName;
                }
                return one;
            }

            if (core.isInstalled(packageName, userId)) {
                installedOrAlready++;
            }
        }

        // hard validation: required trio must be present
        boolean hasGms = core.isInstalled(GMS_PKG, userId);
        boolean hasGsf = core.isInstalled(GSF_PKG, userId);
        boolean hasVending = core.isInstalled(VENDING_PKG, userId);

        if (!hasGms || !hasGsf || !hasVending) {
            InstallResult fail = new InstallResult();
            fail.success = false;
            fail.msg = "Missing required packages after install. "
                    + "gms=" + hasGms
                    + ", gsf=" + hasGsf
                    + ", vending=" + hasVending
                    + ", attempted=" + attempted
                    + ", installedOrAlready=" + installedOrAlready;
            return fail;
        }

        InstallResult ok = new InstallResult();
        ok.success = true;
        ok.msg = "GMS installed";
        return ok;
    }

    public static void uninstallGApps(int userId) {
        BlackBoxCore core = BlackBoxCore.get();
        for (String p : GOOGLE_SERVICE) {
            core.uninstallPackageAsUser(p, userId);
        }
        for (String p : GOOGLE_APP) {
            core.uninstallPackageAsUser(p, userId);
        }
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }

    public static boolean isSupportGms() {
        try {
            BlackBoxCore.getPackageManager().getPackageInfo(GMS_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isInstalledGoogleService(int userId) {
        return BlackBoxCore.get().isInstalled(GMS_PKG, userId);
    }
}
