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
 * updated by alex5402 on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * TFNQw5HgWUS33Ke1eNmSFTwoQySGU7XNsK (USDT TRC20)
 */
public class IAppOpsManagerProxy extends BinderInvocationStub {
    public IAppOpsManagerProxy() {
        super(BRServiceManager.get().getService(Context.APP_OPS_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder call = BRServiceManager.get().getService(Context.APP_OPS_SERVICE);
        return BRIAppOpsServiceStub.get().asInterface(call);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (BRAppOpsManager.get(null)._check_mService() != null) {
            AppOpsManager appOpsManager = (AppOpsManager) BlackBoxCore.getContext().getSystemService(Context.APP_OPS_SERVICE);
            try {
                BRAppOpsManager.get(appOpsManager)._set_mService(getProxyInvocation());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        replaceSystemService(Context.APP_OPS_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // AppOps on Android 11+/12+ validates (uid, package) pairs very strictly.
            // If we rewrite packageName -> host but leave *any* uid as the virtual uid, the server throws
            // "Specified package \"<host>\" under uid <guest> but it is not" and permission checks loop.
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            return super.invoke(proxy, method, args);
        } catch (SecurityException e) {
            // Handle SecurityException for UID/package mismatches
            Slog.w(TAG, "AppOps invoke: SecurityException caught, allowing operation", e);
            return AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Slog.e(TAG, "AppOps invoke: Error in method " + method.getName(), e);
            return super.invoke(proxy, method, args);
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("noteProxyOperation")
    public static class NoteProxyOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return AppOpsManager.MODE_ALLOWED;
        }
    }

    @ProxyMethod("checkPackage")
    public static class CheckPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Android uses this to validate (uid, package). We must not throw here.
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            // If the return type is void, return null; otherwise return MODE_ALLOWED.
            if (method.getReturnType() == Void.TYPE) {
                return null;
            }
            return AppOpsManager.MODE_ALLOWED;
        }
    }

    @ProxyMethod("checkOperation")
    public static class CheckOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            try {
                // args: (int op, int uid, String packageName)
                int op = (int) args[0];
                // On API >= 29, translate to public op name and allow media/storage reads
                String publicName = getOpPublicName(op);
                if (publicName != null && isMediaStorageOrAudioOp(publicName)) {
                    Slog.d(TAG, "AppOps CheckOperation: Allowing operation: " + publicName);
                    return AppOpsManager.MODE_ALLOWED;
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps CheckOperation: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    @ProxyMethod("noteOperation")
    public static class NoteOperation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            // args: (int op, int uid, String packageName)
            try {
                int op = (int) args[0];
                String publicName = getOpPublicName(op);
                if (publicName != null && isMediaStorageOrAudioOp(publicName)) {
                    Slog.d(TAG, "AppOps NoteOperation: Allowing operation: " + publicName);
                    return AppOpsManager.MODE_ALLOWED;
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps NoteOperation: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    @ProxyMethod("checkOpNoThrow")
    public static class CheckOpNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            // args: (String op, int uid, String packageName)
            try {
                String opStr = (String) args[0];
                if (opStr != null && isMediaStorageOrAudioOp(opStr)) {
                    Slog.d(TAG, "AppOps CheckOpNoThrow: Allowing operation: " + opStr);
                    return AppOpsManager.MODE_ALLOWED;
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps CheckOpNoThrow: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    // Android 12+: startOp/startOpNoThrow gate long-running operations like recording
    @ProxyMethod("startOp")
    public static class StartOp extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            try {
                int op = (int) args[0];
                String name = getOpPublicName(op);
                if (name != null && isMediaStorageOrAudioOp(name)) {
                    Slog.d(TAG, "AppOps StartOp: Allowing operation: " + name);
                    return AppOpsManager.MODE_ALLOWED;
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps StartOp: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    @ProxyMethod("startOpNoThrow")
    public static class StartOpNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            try {
                // args: (String op, int uid, String packageName)
                String opStr = (String) args[0];
                if (opStr != null && isMediaStorageOrAudioOp(opStr)) {
                    Slog.d(TAG, "AppOps StartOpNoThrow: Allowing operation: " + opStr);
                    return AppOpsManager.MODE_ALLOWED;
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps StartOpNoThrow: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    // No-op finish so recording sessions don't error out
    @ProxyMethod("finishOp")
    public static class FinishOp extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                int op = (int) args[0];
                String name = getOpPublicName(op);
                if (name != null && isMediaStorageOrAudioOp(name)) {
                    Slog.d(TAG, "AppOps FinishOp: Finishing operation: " + name);
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    // Specific handler for RECORD_AUDIO operations
    @ProxyMethod("noteOp")
    public static class NoteOp extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            try {
                int op = (int) args[0];
                String name = getOpPublicName(op);
                if (name != null && isMediaStorageOrAudioOp(name)) {
                    Slog.d(TAG, "AppOps NoteOp: Allowing operation: " + name);
                    return AppOpsManager.MODE_ALLOWED;
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps NoteOp: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    // Covers AppOpsManager.unsafeCheckOpNoThrow(...) and related binder calls
    @ProxyMethod("unsafeCheckOpNoThrow")
    public static class UnsafeCheckOpNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Covers AppOpsManager.unsafeCheckOpNoThrow(...) and related binder calls
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            try {
                if (args != null && args.length > 0) {
                    Object opObj = args[0];
                    if (opObj instanceof String) {
                        String opStr = (String) opObj;
                        if (isMediaStorageOrAudioOp(opStr)) {
                            Slog.d(TAG, "AppOps UnsafeCheck: Allowing opStr=" + opStr);
                            return AppOpsManager.MODE_ALLOWED;
                        }
                    } else if (opObj instanceof Integer) {
                        int op = (int) opObj;
                        String name = getOpPublicName(op);
                        if (isMediaStorageOrAudioOp(name)) {
                            Slog.d(TAG, "AppOps UnsafeCheck: Allowing op=" + op + " name=" + name);
                            return AppOpsManager.MODE_ALLOWED;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps UnsafeCheck: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    // noteOpNoThrow is used by many apps to gate special access checks (incl. MANAGE_EXTERNAL_STORAGE)
    @ProxyMethod("noteOpNoThrow")
    public static class NoteOpNoThrow extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            MethodParameterUtils.replaceAllUid(args);
            try {
                int op = (int) args[0];
                String name = getOpPublicName(op);
                if (name != null && isMediaStorageOrAudioOp(name)) {
                    Slog.d(TAG, "AppOps NoteOpNoThrow: Allowing operation: " + name);
                    return AppOpsManager.MODE_ALLOWED;
                }
            } catch (Throwable ignored) {
            }
            try {
                return method.invoke(who, args);
            } catch (SecurityException se) {
                Slog.w(TAG, "AppOps NoteOpNoThrow: SecurityException, allowing", se);
                return AppOpsManager.MODE_ALLOWED;
            }
        }
    }

    private static boolean isMediaStorageOrAudioOp(String opPublicNameOrStr) {
        if (opPublicNameOrStr == null) return false;
        // Accept both public names and OPSTR strings
        String n = opPublicNameOrStr.toUpperCase();
        return n.contains("READ_MEDIA")
                || n.contains("READ_EXTERNAL_STORAGE")
                || n.contains("MANAGE_EXTERNAL_STORAGE")
                || n.contains("RECORD_AUDIO")
                || n.contains("CAPTURE_AUDIO_OUTPUT")
                || n.contains("MODIFY_AUDIO_SETTINGS")
                || n.contains("AUDIO")
                || n.contains("MICROPHONE")
                || n.contains("FOREGROUND_SERVICE")
                || n.contains("SYSTEM_ALERT_WINDOW")
                || n.contains("WRITE_SETTINGS")
                || n.contains("ACCESS_FINE_LOCATION")
                || n.contains("ACCESS_COARSE_LOCATION")
                || n.contains("CAMERA")
                || n.contains("BODY_SENSORS")
                || n.contains("BLUETOOTH_SCAN")
                || n.contains("BLUETOOTH_CONNECT")
                || n.contains("BLUETOOTH_ADVERTISE")
                || n.contains("NEARBY_WIFI_DEVICES")
                || n.contains("POST_NOTIFICATIONS");
    }

    private static String getOpPublicName(int op) {
        try {
            // AppOpsManager.opToPublicName was added in API 29
            java.lang.reflect.Method m = AppOpsManager.class.getMethod("opToPublicName", int.class);
            Object name = m.invoke(null, op);
            return name != null ? name.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
