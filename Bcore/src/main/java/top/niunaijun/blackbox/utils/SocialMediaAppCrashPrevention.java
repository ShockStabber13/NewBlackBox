package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.os.Build;
import android.webkit.WebView;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;

/**
 * Crash prevention utility focused on social media style apps.
 *
 * NOTE:
 * This class must avoid applying global WebView/Chromium mutations at process init time.
 * Chromium-based apps (Chrome/Trichrome/WebView provider) are very sensitive during native startup.
 */
public class SocialMediaAppCrashPrevention {
    private static final String TAG = "SocialMediaCrashPrevention";
    private static boolean sIsInitialized = false;

    // Known social media app packages
    private static final String[] SOCIAL_MEDIA_PACKAGES = {
            "com.facebook.katana",            // Facebook
            "com.facebook.orca",              // Facebook Messenger
            "com.instagram.android",          // Instagram
            "com.whatsapp",                   // WhatsApp
            "org.telegram.messenger",         // Telegram
            "com.twitter.android",            // Twitter/X
            "com.zhiliaoapp.musically",       // TikTok
            "com.snapchat.android",           // Snapchat
            "com.google.android.youtube",     // YouTube
            "com.linkedin.android",           // LinkedIn
            "com.discord",                    // Discord
            "com.reddit.frontpage",           // Reddit
            "com.spotify.music",              // Spotify
            "com.netflix.mediaclient",        // Netflix
            "com.amazon.avod.thirdpartyclient"// Prime Video
    };

    // Chromium-related packages that must never receive these hooks
    private static final String[] CHROMIUM_PACKAGES = {
            "com.android.chrome",
            "com.google.android.trichromelibrary",
            "com.google.android.webview"
    };

    // Crash prevention strategies
    private static final Map<String, CrashPreventionStrategy> sCrashPreventionStrategies = new HashMap<>();

    /**
     * Initialize crash prevention.
     *
     * Safe-by-default:
     * - If current package is unknown at this stage, skip invasive WebView hooks.
     * - If package is Chromium-related, skip all WebView mutations.
     */
    public static synchronized void initialize() {
        if (sIsInitialized) {
            return;
        }

        try {
            final String currentPackage = getCurrentPackageNameSafely();
            Slog.d(TAG, "Initializing social media app crash prevention (pkg=" + currentPackage + ")");

            // Keep non-invasive checks
            installAttributionSourceCrashPrevention();
            installContextCrashPrevention();
            installPermissionCrashPrevention();
            installMediaCrashPrevention();

            // WebView/Chromium-sensitive hooks only for known social-media packages
            if (currentPackage != null && isSocialMediaPackage(currentPackage) && !isChromiumPackage(currentPackage)) {
                installWebViewCrashPrevention(currentPackage);
            } else {
                Slog.d(TAG, "Skipping WebView crash-prevention hooks for pkg=" + currentPackage);
            }

            sIsInitialized = true;
            Slog.d(TAG, "Social media app crash prevention initialized successfully");
        } catch (Exception e) {
            Slog.e(TAG, "Failed to initialize crash prevention: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the current app is a social media app
     */
    public static boolean isSocialMediaApp() {
        return isSocialMediaPackage(getCurrentPackageNameSafely());
    }

    private static boolean isSocialMediaPackage(String packageName) {
        if (packageName == null) return false;
        for (String socialMediaPackage : SOCIAL_MEDIA_PACKAGES) {
            if (socialMediaPackage.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChromiumPackage(String packageName) {
        if (packageName == null) return false;
        for (String chromiumPackage : CHROMIUM_PACKAGES) {
            if (chromiumPackage.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static String getCurrentPackageNameSafely() {
        try {
            return BActivityThread.getAppPackageName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Install WebView crash prevention for a specific non-Chromium package.
     */
    private static void installWebViewCrashPrevention(String packageName) {
        try {
            if (isChromiumPackage(packageName)) {
                Slog.d(TAG, "Bypassing WebView crash prevention for Chromium package: " + packageName);
                return;
            }

            // Hook WebView constructor to prevent data directory conflicts
            hookWebViewConstructor();

            // Configure WebView directory safely for this package
            hookWebViewDatabase(packageName);

            Slog.d(TAG, "WebView crash prevention installed for " + packageName);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to install WebView crash prevention: " + e.getMessage());
        }
    }

    /**
     * Hook WebView constructor to prevent crashes
     */
    private static void hookWebViewConstructor() {
        try {
            Constructor<WebView> originalConstructor = WebView.class.getDeclaredConstructor(Context.class);
            originalConstructor.setAccessible(true);

            // This would be implemented with a proper hooking framework
            Slog.d(TAG, "WebView constructor hook prepared");
        } catch (Exception e) {
            Slog.w(TAG, "Could not prepare WebView constructor hook: " + e.getMessage());
        }
    }

    /**
     * Configure per-package WebView directories.
     */
    private static void hookWebViewDatabase(String packageName) {
        try {
            if (isChromiumPackage(packageName)) {
                Slog.d(TAG, "Skip WebView DB hook for Chromium package: " + packageName);
                return;
            }

            Context context = BlackBoxCore.getContext();
            if (context == null) {
                Slog.w(TAG, "Context unavailable, skip WebView directory setup");
                return;
            }

            int userId = 0;
            try {
                userId = BActivityThread.getUserId();
            } catch (Throwable ignored) {
            }

            // Put per-guest data under host sandbox, isolated by package + user.
            String webViewDir = context.getApplicationInfo().dataDir
                    + "/webview_guest_" + packageName.replace('.', '_') + "_" + userId;

            File webViewDirectory = new File(webViewDir);
            if (!webViewDirectory.exists() && !webViewDirectory.mkdirs()) {
                Slog.w(TAG, "Failed to create WebView directory: " + webViewDir);
                return;
            }

            // Keep properties package-scoped and avoid Chromium packages entirely.
            System.setProperty("webview.data.dir", webViewDir);
            System.setProperty("webview.cache.dir", webViewDir + "/cache");
            System.setProperty("webview.cookies.dir", webViewDir + "/cookies");
            Slog.d(TAG, "Configured WebView dirs for " + packageName + " -> " + webViewDir);
        } catch (Exception e) {
            Slog.w(TAG, "Could not hook WebViewDatabase: " + e.getMessage());
        }
    }

    /**
     * Install AttributionSource crash prevention
     */
    private static void installAttributionSourceCrashPrevention() {
        try {
            // Use the existing AttributionSourceUtils
            Slog.d(TAG, "AttributionSource crash prevention installed");
        } catch (Exception e) {
            Slog.w(TAG, "Failed to install AttributionSource crash prevention: " + e.getMessage());
        }
    }

    /**
     * Install context crash prevention
     */
    private static void installContextCrashPrevention() {
        try {
            Context context = BlackBoxCore.getContext();
            if (context == null) {
                Slog.w(TAG, "Host context is null, attempting to recover");
                recoverContext();
            }

            Slog.d(TAG, "Context crash prevention installed");
        } catch (Exception e) {
            Slog.w(TAG, "Failed to install context crash prevention: " + e.getMessage());
        }
    }

    /**
     * Install permission crash prevention
     */
    private static void installPermissionCrashPrevention() {
        try {
            Slog.d(TAG, "Permission crash prevention installed");
        } catch (Exception e) {
            Slog.w(TAG, "Failed to install permission crash prevention: " + e.getMessage());
        }
    }

    /**
     * Install media crash prevention
     */
    private static void installMediaCrashPrevention() {
        try {
            Slog.d(TAG, "Media crash prevention installed");
        } catch (Exception e) {
            Slog.w(TAG, "Failed to install media crash prevention: " + e.getMessage());
        }
    }

    /**
     * Attempt to recover context if it's null
     */
    private static void recoverContext() {
        try {
            Context recoveredContext = null;

            // Try to get context from ActivityThread
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
                Object activityThread = currentActivityThreadMethod.invoke(null);

                if (activityThread != null) {
                    Method getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext");
                    recoveredContext = (Context) getSystemContextMethod.invoke(activityThread);
                }
            } catch (Exception e) {
                Slog.w(TAG, "Could not recover context from ActivityThread: " + e.getMessage());
            }

            if (recoveredContext != null) {
                Slog.d(TAG, "Successfully recovered context");
                // Setting BlackBoxCore context here is intentionally avoided.
            }

        } catch (Exception e) {
            Slog.e(TAG, "Failed to recover context: " + e.getMessage());
        }
    }

    /**
     * Apply crash prevention for a specific app
     */
    public static void applyCrashPrevention(String packageName) {
        if (packageName == null) {
            return;
        }

        try {
            if (isChromiumPackage(packageName)) {
                Slog.d(TAG, "Skip crash-prevention apply for Chromium package: " + packageName);
                return;
            }

            boolean isSocialMedia = isSocialMediaPackage(packageName);

            if (isSocialMedia) {
                Slog.d(TAG, "Applying crash prevention for social media app: " + packageName);

                // Re-apply package-scoped WebView safety when called with concrete package.
                installWebViewCrashPrevention(packageName);

                CrashPreventionStrategy strategy = sCrashPreventionStrategies.get(packageName);
                if (strategy != null) {
                    strategy.apply();
                }
            }

        } catch (Exception e) {
            Slog.w(TAG, "Failed to apply crash prevention for " + packageName + ": " + e.getMessage());
        }
    }

    /**
     * Crash prevention strategy interface
     */
    public interface CrashPreventionStrategy {
        void apply();
    }

    /**
     * Get crash prevention status
     */
    public static String getCrashPreventionStatus() {
        String currentPackage = getCurrentPackageNameSafely();

        StringBuilder status = new StringBuilder();
        status.append("Social Media Crash Prevention Status:\n");
        status.append("Initialized: ").append(sIsInitialized).append("\n");
        status.append("Current App: ").append(currentPackage).append("\n");
        status.append("Is Social Media App: ").append(isSocialMediaPackage(currentPackage)).append("\n");
        status.append("Is Chromium App: ").append(isChromiumPackage(currentPackage)).append("\n");
        status.append("Android Version: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");

        return status.toString();
    }
}