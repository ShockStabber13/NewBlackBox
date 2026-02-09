package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;
import android.os.Build;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Proxy for android.app.ILocaleManager (Android 13+).
 *
 * Prevents app crashes when LocaleManager enforces strict package/uid or privileged permission checks.
 */
public class ILocaleManagerProxy extends BinderInvocationStub {

    private static final String TAG = "ILocaleManagerProxy";
    private static final String LOCALE_SERVICE = "locale";

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService(LOCALE_SERVICE));
    }

    @Override
    protected Object getWho() {
        if (Build.VERSION.SDK_INT < 33) {
            return null;
        }

        IBinder binder = BRServiceManager.get().getService(LOCALE_SERVICE);
        if (binder == null) {
            return null;
        }

        try {
            Class<?> stubClass = Class.forName("android.app.ILocaleManager$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            asInterface.setAccessible(true);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to bind locale service", e);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(LOCALE_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // Normalize virtual package/uid pairs for platform validation.
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            return super.invoke(proxy, method, args);
        } catch (SecurityException se) {
            // Some locale operations are privileged on certain OEM/Android builds.
            // Return safe defaults instead of crashing guest process.
            Slog.w(TAG, "SecurityException in " + method.getName() + ", returning safe default", se);
            return getDefaultReturnValue(method);
        }
    }

    private Object getDefaultReturnValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) return null;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Double.TYPE) return 0d;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Character.TYPE) return '\0';
        return null;
    }
}
