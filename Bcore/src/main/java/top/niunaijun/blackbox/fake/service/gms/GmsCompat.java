package top.niunaijun.blackbox.fake.service.gms;

public final class GmsCompat {
    private static final String PROP = "persist.blackbox.gms_compat";

    private GmsCompat() {}

    public static boolean enabled() {
        // default ON if property not readable
        return getBooleanSystemProperty(PROP, true);
    }

    public static boolean isGoogleFamily(String pkgOrAuthority) {
        if (pkgOrAuthority == null) return false;
        String s = pkgOrAuthority.toLowerCase();
        return s.startsWith("com.google.android.gms")
                || s.startsWith("com.google.android.gsf")
                || s.startsWith("com.android.vending")
                || s.contains("com.google.android.gms")
                || s.contains("com.google.android.gsf")
                || s.contains("com.android.vending");
    }

    private static boolean getBooleanSystemProperty(String key, boolean defValue) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = sp.getDeclaredMethod("getBoolean", String.class, boolean.class);
            Object out = m.invoke(null, key, defValue);
            return (out instanceof Boolean) ? (Boolean) out : defValue;
        } catch (Throwable ignored) {
            return defValue;
        }
    }
}
