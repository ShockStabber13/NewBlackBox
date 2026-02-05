package top.niunaijun.blackbox.utils;

import android.os.Binder;
import android.os.Process;
import android.os.Bundle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;

/**
 * Fixes AttributionSource so framework UID checks pass on Android 12+.
 */
public class AttributionSourceUtils {
    private static final String TAG = "AttributionSourceUtils";

    public static void fixAttributionSourceInArgs(Object[] args) {
        if (args == null) return;

        for (Object arg : args) {
            if (arg == null) continue;

            final String cn = arg.getClass().getName();
            try {
                if (cn.contains("AttributionSource")) {
                    fixAttributionSource(arg);
                } else if (arg instanceof Bundle) {
                    fixAttributionSourceInBundle((Bundle) arg);
                }
            } catch (Throwable t) {
                Slog.w(TAG, "fixAttributionSourceInArgs error: " + t.getMessage());
            }
        }
    }

    private static int getSafeUid() {
        int calling = Binder.getCallingUid();
        if (calling > 0) return calling;
        return Process.myUid();
    }

    private static String getSafePackage() {
        try {
            String host = BlackBoxCore.getHostPkg();
            if (host != null && !host.isEmpty()) return host;
        } catch (Throwable ignored) {}
        return "android";
    }
// Add inside AttributionSourceUtils class:

    public static Object createSafeAttributionSource() {
        try {
            // Try builder path (Android 12+)
            Class<?> asClass = Class.forName("android.content.AttributionSource");
            Class<?> builderClass = Class.forName("android.content.AttributionSource$Builder");
            java.lang.reflect.Constructor<?> c = builderClass.getDeclaredConstructor(int.class);
            c.setAccessible(true);

            int uid = android.os.Binder.getCallingUid();
            if (uid <= 0) uid = android.os.Process.myUid();

            Object builder = c.newInstance(uid);

            try {
                java.lang.reflect.Method setPkg = builderClass.getDeclaredMethod("setPackageName", String.class);
                setPkg.setAccessible(true);
                String pkg = top.niunaijun.blackbox.BlackBoxCore.getHostPkg();
                if (pkg == null || pkg.isEmpty()) pkg = "android";
                setPkg.invoke(builder, pkg);
            } catch (Throwable ignored) {}

            java.lang.reflect.Method build = builderClass.getDeclaredMethod("build");
            build.setAccessible(true);
            return build.invoke(builder);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static void fixAttributionSourceUid(Object attributionSource) {
        if (attributionSource == null) return;
        try {
            int uid = android.os.Binder.getCallingUid();
            if (uid <= 0) uid = android.os.Process.myUid();

            Class<?> c = attributionSource.getClass();

            // common uid fields
            String[] fields = {"mUid", "uid", "mCallingUid", "callingUid", "mSourceUid", "sourceUid"};
            for (String f : fields) {
                try {
                    java.lang.reflect.Field field = c.getDeclaredField(f);
                    field.setAccessible(true);
                    field.setInt(attributionSource, uid);
                } catch (Throwable ignored) {}
            }

            // optional setter
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod("setUid", int.class);
                m.setAccessible(true);
                m.invoke(attributionSource, uid);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public static void fixAttributionSource(Object attributionSource) {
        if (attributionSource == null) return;

        int safeUid = getSafeUid();
        String safePkg = getSafePackage();

        Class<?> c = attributionSource.getClass();

        // UID fields
        String[] uidFields = {
                "mUid", "uid", "mCallingUid", "callingUid", "mSourceUid", "sourceUid"
        };
        for (String f : uidFields) {
            try {
                Field field = c.getDeclaredField(f);
                field.setAccessible(true);
                field.setInt(attributionSource, safeUid);
            } catch (Throwable ignored) {}
        }

        // package fields
        String[] pkgFields = {
                "mPackageName", "packageName", "mAttributionTag", "mSourcePackage", "sourcePackage"
        };
        for (String f : pkgFields) {
            try {
                Field field = c.getDeclaredField(f);
                field.setAccessible(true);
                Object old = field.get(attributionSource);
                if (old instanceof String && (f.equals("mAttributionTag"))) {
                    // attributionTag can be null safely; don't force host package into tag
                    continue;
                }
                field.set(attributionSource, safePkg);
            } catch (Throwable ignored) {}
        }

        // try setter methods too
        try {
            Method m = c.getDeclaredMethod("setUid", int.class);
            m.setAccessible(true);
            m.invoke(attributionSource, safeUid);
        } catch (Throwable ignored) {}

        try {
            Method m = c.getDeclaredMethod("setPackageName", String.class);
            m.setAccessible(true);
            m.invoke(attributionSource, safePkg);
        } catch (Throwable ignored) {}

        // fix "next" chain recursively if present
        String[] nextFields = {"mNext", "next"};
        for (String nf : nextFields) {
            try {
                Field f = c.getDeclaredField(nf);
                f.setAccessible(true);
                Object next = f.get(attributionSource);
                if (next != null && next.getClass().getName().contains("AttributionSource")) {
                    fixAttributionSource(next);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void fixAttributionSourceInBundle(Bundle b) {
        if (b == null) return;
        try {
            Set<String> keys = b.keySet();
            for (String k : keys) {
                Object v = b.get(k);
                if (v != null && v.getClass().getName().contains("AttributionSource")) {
                    fixAttributionSource(v);
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "fixAttributionSourceInBundle error: " + t.getMessage());
        }
    }
}
