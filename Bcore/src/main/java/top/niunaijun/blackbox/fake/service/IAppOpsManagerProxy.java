package top.niunaijun.blackbox.fake.service;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.app.BRAppOpsManager;
import black.android.os.BRServiceManager;
import black.com.android.internal.app.BRIAppOpsServiceStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

/**
 * AppOps proxy:
 * - rewrites pkg/uid pairs consistently
 * - avoids blanket MODE_ALLOWED fallbacks
 * - only soft-falls back for checkPackage-like validation paths
 */
public class IAppOpsManagerProxy extends BinderInvocationStub {
    private static final String TAG = "IAppOpsManagerProxy";

    public IAppOpsManagerProxy() {
        super(BRServiceManager.get().getService(Context.APP_OPS_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(Context.APP_OPS_SERVICE);
        return BRIAppOpsServiceStub.get().asInterface(binder);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (BRAppOpsManager.get(null)._check_mService() != null) {
            AppOpsManager appOpsManager =
                    (AppOpsManager) BlackBoxCore.getContext().getSystemService(Context.APP_OPS_SERVICE);
            try {
                BRAppOpsManager.get(appOpsManager)._set_mService(getProxyInvocation());
            } catch (Throwable e) {
                Slog.w(TAG, "inject mService failed", e);
            }
        }
        replaceSystemService(Context.APP_OPS_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Global rewrite before dispatch.
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceAllUid(args);
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private static Object invokeWithRewrite(Object who, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceAllUid(args);
        return method.invoke(who, args);
    }

    private static Object allowByReturnType(Method method) {
        Class<?> rt = method.getReturnType();
        if (rt == void.class || rt == Void.class) return null;
        if (rt == int.class || rt == Integer.class) return AppOpsManager.MODE_ALLOWED;
        if (rt == boolean.class || rt == Boolean.class) return true;
        return null;
    }

    @ProxyMethod("noteProxyOperation")
    public static class NoteProxyOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Keep permissive for proxy-note path to reduce guest breakage.
            return allowByReturnType(method);
        }
    }

    @ProxyMethod("checkPackage")
    public static class CheckPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return invokeWithRewrite(who, method, args);
            } catch (SecurityException e) {
                // Soft fallback only for strict uid/pkg validator.
                Slog.w(TAG, "checkPackage SecurityException; allow fallback", e);
                return allowByReturnType(method);
            }
        }
    }

    @ProxyMethod("checkOperation")
    public static class CheckOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("noteOperation")
    public static class NoteOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("checkOpNoThrow")
    public static class CheckOpNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("startOp")
    public static class StartOp extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("startOpNoThrow")
    public static class StartOpNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("finishOp")
    public static class FinishOp extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    // Common alternate names on different Android versions / ROMs

    @ProxyMethod("checkOp")
    public static class CheckOp extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("noteOp")
    public static class NoteOp extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("noteOpNoThrow")
    public static class NoteOpNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("startOperation")
    public static class StartOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("finishOperation")
    public static class FinishOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("noteOperationRaw")
    public static class NoteOperationRaw extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("checkOperationRaw")
    public static class CheckOperationRaw extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("checkAudioOperation")
    public static class CheckAudioOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }

    @ProxyMethod("unsafeCheckOpRawNoThrow")
    public static class UnsafeCheckOpRawNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return invokeWithRewrite(who, method, args);
        }
    }
}
