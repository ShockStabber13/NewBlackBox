package top.niunaijun.blackbox.fake.service.context.providers;

import android.net.Uri;
import android.os.Bundle;
import android.os.IInterface;
import android.text.TextUtils;

import java.lang.reflect.Method;

import black.android.content.BRAttributionSource;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;

/**
 * updated by alex5402 on 4/8/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * TFNQw5HgWUS33Ke1eNmSFTwoQySGU7XNsK (USDT TRC20)
 */
public class ContentProviderStub extends ClassInvocationStub implements BContentProvider {
    public static final String TAG = "ContentProviderStub";
    private static final String AMAZON_MAPINFO_PREFIX = "com.amazon.identity.auth.device.MapInfoProvider";

    private IInterface mBase;
    private String mAppPkg;

    public IInterface wrapper(final IInterface contentProviderProxy, final String appPkg) {
        mBase = contentProviderProxy;
        mAppPkg = appPkg;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }

        final String methodName = method.getName();
        final String authority = extractAuthority(args);
        final boolean amazonMapInfo = isAmazonMapInfoAuthority(authority);

        // Amazon MapInfo provider is signature/uid sensitive.
        // Ensure we do NOT send host identity for this provider.
        if (amazonMapInfo) {
            rewriteAmazonIdentityArgs(args);
        }

        // Fix AttributionSource and package name issues
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof String) {
                    String strArg = (String) arg;

                    // DO NOT rewrite non-package strings (these show up in provider calls):
                    // - MIME types like "video/*"
                    // - URIs like "content://..." / "file://..."
                    // - absolute paths like "/sdcard/..."
                    if (strArg.startsWith("content:") || strArg.startsWith("file:") || strArg.startsWith("/") || strArg.contains("/")) {
                        continue;
                    }

                    // Don't replace system provider authorities
                    if (!isSystemProviderAuthority(strArg)) {
                        // keep original behavior for non-amazon paths
                    }
                } else if (arg != null && arg.getClass().getName().equals(BRAttributionSource.getRealClass().getName())) {
                    // Fix AttributionSource UID
                    try {
                        if (amazonMapInfo) {
                            fixAttributionSourceUid(arg);
                        } else {
                            int uid = android.os.Binder.getCallingUid();
                            if (uid <= 0) uid = android.os.Process.myUid();
                            ContextCompat.fixAttributionSourceState(arg, uid);
                        }
                    } catch (Exception e) {
                        // If fixing AttributionSource fails, try to create a new one or skip
                        Slog.w(TAG, "Failed to fix AttributionSource, continuing with original");
                    }
                } else if (arg != null && arg.getClass().getName().contains("AttributionSource")) {
                    // Handle any AttributionSource-like objects that might cause UID issues
                    try {
                        // Try to fix UID using reflection
                        fixAttributionSourceUid(arg);
                    } catch (Exception e) {
                        Slog.w(TAG, "Failed to fix AttributionSource-like object: " + e.getMessage());
                    }
                }
            }
        }

        // Pre-validate the call to prevent system-level SecurityException
        if (methodName.equals("query") || methodName.equals("insert") ||
                methodName.equals("update") || methodName.equals("delete") ||
                methodName.equals("bulkInsert") || methodName.equals("call")) {

            // Check if this is likely to cause a UID mismatch
            try {
                return method.invoke(mBase, args);
            } catch (Throwable e) {
                // Handle SecurityException and other UID-related errors
                Throwable cause = e.getCause();
                if (isUidMismatchError(cause)) {
                    if (amazonMapInfo) {
                        // For Amazon MapInfo, do NOT mask with fake defaults.
                        // Let the caller receive the real exception if rewrite wasn't enough.
                        throw (cause != null ? cause : e);
                    }
                    throw cause;
                } else if (cause instanceof RuntimeException) {
                    String message = cause.getMessage();
                    if (message != null && (message.contains("uid") || message.contains("permission"))) {
                        Slog.w(TAG, "Permission/UID error in ContentProvider call, returning safe default: " + message);
                        return getSafeDefaultValue(methodName, method.getReturnType());
                    }
                }

                // For call method specifically, always return safe default on any error
                if (methodName.equals("call")) {
                    Slog.w(TAG, "Error in call method, returning safe default: " + e.getMessage());
                    return getSafeDefaultValue(methodName, method.getReturnType());
                }

                throw (cause != null ? cause : e);
            }
        }

        // For other methods, proceed normally
        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            // Handle SecurityException for UID mismatch in any method
            Throwable cause = e.getCause();
            if (isUidMismatchError(cause)) {
                if (amazonMapInfo) {
                    throw (cause != null ? cause : e);
                }
                Slog.w(TAG, "UID mismatch in " + methodName + ", returning safe default: " + cause.getMessage());
                return getSafeDefaultValue(methodName, method.getReturnType());
            }
            throw (cause != null ? cause : e);
        }
    }

    private String extractAuthority(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Uri) {
                return ((Uri) arg).getAuthority();
            }
            if (arg instanceof String) {
                String s = (String) arg;
                if (s.startsWith("content://")) {
                    try {
                        return Uri.parse(s).getAuthority();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private boolean isAmazonMapInfoAuthority(String authority) {
        return authority != null && authority.startsWith(AMAZON_MAPINFO_PREFIX);
    }

    private String getBestAppPkg() {
        String appPkg = BActivityThread.getAppPackageName();
        if (TextUtils.isEmpty(appPkg)) {
            appPkg = mAppPkg;
        }
        return appPkg;
    }

    private void rewriteAmazonIdentityArgs(Object[] args) {
        if (args == null) return;

        String hostPkg = BlackBoxCore.getHostPkg();
        String appPkg = getBestAppPkg();
        if (TextUtils.isEmpty(hostPkg) || TextUtils.isEmpty(appPkg)) {
            return;
        }

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof String) {
                if (hostPkg.equals(arg)) {
                    args[i] = appPkg;
                }
            } else if (arg instanceof Bundle) {
                rewriteBundleHostPkg((Bundle) arg, hostPkg, appPkg);
            } else if (arg != null && arg.getClass().getName().contains("AttributionSource")) {
                fixAttributionSourceUid(arg);
            }
        }
    }

    private void rewriteBundleHostPkg(Bundle b, String hostPkg, String appPkg) {
        if (b == null) return;
        final String[] keys = new String[] {
                "calling_package",
                "callingPackage",
                "caller_package"
        };
        for (String k : keys) {
            try {
                String value = b.getString(k);
                if (hostPkg.equals(value)) {
                    b.putString(k, appPkg);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private Object getSafeDefaultValue(String methodName) {
        switch (methodName) {
            case "query":
                return null; // Return null cursor
            case "insert":
                return null; // Return null URI
            case "update":
            case "delete":
                return 0; // Return 0 rows affected
            case "bulkInsert":
                return 0; // Return 0 rows inserted
            case "call":
                return null; // Return null for call method
            case "getType":
                return null; // Return null MIME type
            case "openFile":
                return null; // Return null ParcelFileDescriptor
            case "openAssetFile":
                return null; // Return null AssetFileDescriptor
            default:
                return null; // Default fallback
        }
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;

        // Check for system provider authorities that need special handling
        return authority.equals("settings") ||
                authority.equals("settings_global") ||
                authority.equals("settings_system") ||
                authority.equals("settings_secure") ||
                authority.equals("media") ||
                authority.equals("telephony") ||
                authority.startsWith("android.provider.Settings");
    }

    /**
     * Enhanced UID mismatch detection and handling
     */
    private boolean isUidMismatchError(Throwable error) {
        if (error == null) return false;

        String message = error.getMessage();
        if (message == null) return false;

        // Check for UID mismatch patterns
        return message.contains("Calling uid") &&
                message.contains("doesn't match source uid") ||
                message.contains("uid") &&
                        message.contains("permission") ||
                message.contains("SecurityException") ||
                message.contains("UID mismatch") ||
                message.contains("signed with a different cert") ||
                message.contains("Unauthorized caller");
    }

    /**
     * Get safe default value based on method name and return type
     */
    private Object getSafeDefaultValue(String methodName, Class<?> returnType) {
        if (returnType == null) {
            return getSafeDefaultValue(methodName);
        }

        // Return type-specific safe defaults
        if (returnType == String.class) {
            return "true"; // Safe default for strings
        } else if (returnType == int.class || returnType == Integer.class) {
            return 1; // Safe default for integers
        } else if (returnType == long.class || returnType == Long.class) {
            return 1L; // Safe default for longs
        } else if (returnType == float.class || returnType == Float.class) {
            return 1.0f; // Safe default for floats
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            return true; // Safe default for booleans
        } else if (returnType == Bundle.class) {
            return new Bundle(); // Safe default for bundles
        }

        // Fallback to method-specific defaults
        return getSafeDefaultValue(methodName);
    }

    /**
     * Fix AttributionSource UID to prevent crashes on Android 12+
     */
    private void fixAttributionSourceUid(Object attributionSource) {
        try {
            if (attributionSource == null) return;

            Class<?> attributionSourceClass = attributionSource.getClass();
            final int effectiveUid = android.os.Process.myUid() > 0
                    ? android.os.Process.myUid()
                    : BActivityThread.getBUid();
            final String appPkg = getBestAppPkg();

            // Try to find and set the UID field
            try {
                java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("mUid");
                uidField.setAccessible(true);
                uidField.set(attributionSource, effectiveUid);
                Slog.d(TAG, "Fixed AttributionSource UID via field access");
            } catch (NoSuchFieldException e) {
                // Try alternative field names
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("uid");
                    uidField.setAccessible(true);
                    uidField.set(attributionSource, effectiveUid);
                    Slog.d(TAG, "Fixed AttributionSource UID via alternative field");
                } catch (NoSuchFieldException e2) {
                    // Try using setter method
                    try {
                        java.lang.reflect.Method setUidMethod = attributionSourceClass.getDeclaredMethod("setUid", int.class);
                        setUidMethod.setAccessible(true);
                        setUidMethod.invoke(attributionSource, effectiveUid);
                        Slog.d(TAG, "Fixed AttributionSource UID via setter method");
                    } catch (Exception e3) {
                        Slog.w(TAG, "Could not fix AttributionSource UID: " + e3.getMessage());
                    }
                }
            }

            // Also try to fix package name
            if (!TextUtils.isEmpty(appPkg)) {
                try {
                    java.lang.reflect.Field packageField = attributionSourceClass.getDeclaredField("mPackageName");
                    packageField.setAccessible(true);
                    packageField.set(attributionSource, appPkg);
                    Slog.d(TAG, "Fixed AttributionSource package name");
                } catch (Exception e) {
                    // Ignore package name fixing errors
                }
            }

        } catch (Exception e) {
            Slog.w(TAG, "Error fixing AttributionSource UID: " + e.getMessage());
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
