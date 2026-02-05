package top.niunaijun.blackbox.fake.delegate;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Field;

/**
 * Android 12+ includes an AttributionSource in many Binder parcels (ContentProvider, AppOps, etc).
 * In app-level virtualization, the ContextImpl may still carry the host uid/package attribution,
 * which causes:
 *   SecurityException: Calling uid X doesn't match source uid Y
 *
 * Fix by forcing ContextImpl.mAttributionSource to match the virtual app uid/package.
 */
public final class AttributionSourceFixer {

    private AttributionSourceFixer() {}

    public static void fix(Context context, int virtualUid, String virtualPackageName) {
        if (context == null || virtualPackageName == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        try {
            // android.content.AttributionSource is public on Android 12+
            Class<?> asClz = Class.forName("android.content.AttributionSource");
            Class<?> builderClz = Class.forName("android.content.AttributionSource$Builder");

            Object builder = builderClz.getConstructor(int.class).newInstance(virtualUid);

            // setPackageName(String)
            try {
                builderClz.getMethod("setPackageName", String.class).invoke(builder, virtualPackageName);
            } catch (Throwable ignored) {}

            // build()
            Object attributionSource = builderClz.getMethod("build").invoke(builder);

            // ContextImpl has mAttributionSource; Context may be a ContextWrapper, so unwrap.
            Context base = context;
            while (true) {
                // ContextWrapper has mBase
                if (!base.getClass().getName().equals("android.content.ContextWrapper")) break;
                Field mBase = base.getClass().getDeclaredField("mBase");
                mBase.setAccessible(true);
                Object inner = mBase.get(base);
                if (inner instanceof Context) {
                    base = (Context) inner;
                } else {
                    break;
                }
            }

            // Try set field on ContextImpl first
            if (base != null) {
                setAttributionField(base, attributionSource);
            }
            // Also try on original context (in case above didn't unwrap)
            if (base != context) {
                setAttributionField(context, attributionSource);
            }
        } catch (Throwable ignored) {
            // best effort: if reflection fails, don't crash the virtual app
        }
    }

    private static void setAttributionField(Object ctx, Object attributionSource) throws Exception {
        Class<?> c = ctx.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("mAttributionSource");
                f.setAccessible(true);
                f.set(ctx, attributionSource);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
    }
}
