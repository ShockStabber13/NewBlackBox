package top.niunaijun.blackbox.fake.delegate;


import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.content.ClipData;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.view.KeyEvent;
import android.view.MotionEvent;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.utils.Reflector;

public class BaseInstrumentationDelegate extends Instrumentation {

    protected Instrumentation mBaseInstrumentation;


    @Override
    public void onCreate(Bundle arguments) {
        mBaseInstrumentation.onCreate(arguments);
    }

    @Override
    public void start() {
        mBaseInstrumentation.start();
    }

    @Override
    public void onStart() {
        mBaseInstrumentation.onStart();
    }

    @Override
    public boolean onException(Object obj, Throwable e) {
        return mBaseInstrumentation.onException(obj, e);
    }

    @Override
    public void sendStatus(int resultCode, Bundle results) {
        mBaseInstrumentation.sendStatus(resultCode, results);
    }

    @Override
    public void addResults(Bundle results) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBaseInstrumentation.addResults(results);
        } else {
            // For Android versions below API 26 (Oreo), implement alternative result handling
            try {
                // Store results in a way that's compatible with older Android versions
                if (results != null && !results.isEmpty()) {
                    // Use sendStatus to send results (available on all Android versions)
                    mBaseInstrumentation.sendStatus(0, results);
                    
                    // Alternative: Store results in a static map for later retrieval
                    storeResultsForOlderVersions(results);
                    
                    // Alternative: Use SharedPreferences to store results
                    storeResultsInPreferences(results);
                }
            } catch (Exception e) {
                android.util.Log.w("BaseInstrumentationDelegate", "Failed to handle results on older Android version: " + e.getMessage());
            }
        }
    }
    
    /**
     * Store results in a static map for older Android versions
     */
    private void storeResultsForOlderVersions(Bundle results) {
        try {
            // Use reflection to access a static results storage
            Class<?> resultsStorageClass = Class.forName("top.niunaijun.blackbox.utils.ResultsStorage");
            java.lang.reflect.Method storeMethod = resultsStorageClass.getMethod("storeResults", String.class, Bundle.class);
            
            // Generate a unique key for these results
            String resultKey = "results_" + System.currentTimeMillis() + "_" + android.os.Process.myPid();
            storeMethod.invoke(null, resultKey, results);
            
            android.util.Log.d("BaseInstrumentationDelegate", "Stored results with key: " + resultKey);
        } catch (Exception e) {
            android.util.Log.w("BaseInstrumentationDelegate", "Failed to store results in static storage: " + e.getMessage());
        }
    }
    
    /**
     * Store results in SharedPreferences for older Android versions
     */
    private void storeResultsInPreferences(Bundle results) {
        try {
            Context context = getContext();
            if (context != null) {
                android.content.SharedPreferences prefs = context.getSharedPreferences("instrumentation_results", Context.MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = prefs.edit();
                
                // Convert Bundle to key-value pairs and store in preferences
                for (String key : results.keySet()) {
                    Object value = results.get(key);
                    if (value instanceof String) {
                        editor.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    }
                }
                
                // Add timestamp for result identification
                editor.putLong("timestamp", System.currentTimeMillis());
                editor.putInt("pid", android.os.Process.myPid());
                
                editor.apply();
                android.util.Log.d("BaseInstrumentationDelegate", "Stored results in SharedPreferences");
            }
        } catch (Exception e) {
            android.util.Log.w("BaseInstrumentationDelegate", "Failed to store results in preferences: " + e.getMessage());
        }
    }

    @Override
    public void finish(int resultCode, Bundle results) {
        mBaseInstrumentation.finish(resultCode, results);
    }

    @Override
    public void setAutomaticPerformanceSnapshots() {
        mBaseInstrumentation.setAutomaticPerformanceSnapshots();
    }

    @Override
    public void startPerformanceSnapshot() {
        mBaseInstrumentation.startPerformanceSnapshot();
    }

    @Override
    public void endPerformanceSnapshot() {
        mBaseInstrumentation.endPerformanceSnapshot();
    }

    @Override
    public void onDestroy() {
        mBaseInstrumentation.onDestroy();
    }

    @Override
    public Context getContext() {
        return mBaseInstrumentation.getContext();
    }

    @Override
    public ComponentName getComponentName() {
        return mBaseInstrumentation.getComponentName();
    }

    @Override
    public Context getTargetContext() {
        return mBaseInstrumentation.getTargetContext();
    }

    @Override
    public boolean isProfiling() {
        return mBaseInstrumentation.isProfiling();
    }

    @Override
    public void startProfiling() {
        mBaseInstrumentation.startProfiling();
    }

    @Override
    public void stopProfiling() {
        mBaseInstrumentation.stopProfiling();
    }

    @Override
    public void setInTouchMode(boolean inTouch) {
        mBaseInstrumentation.setInTouchMode(inTouch);
    }

    @Override
    public void waitForIdle(Runnable recipient) {
        mBaseInstrumentation.waitForIdle(recipient);
    }

    @Override
    public void waitForIdleSync() {
        mBaseInstrumentation.waitForIdleSync();
    }

    @Override
    public void runOnMainSync(Runnable runner) {
        mBaseInstrumentation.runOnMainSync(runner);
    }

    @Override
    public Activity startActivitySync(Intent intent) {
        return mBaseInstrumentation.startActivitySync(intent);
    }

    @Override
    public void addMonitor(ActivityMonitor monitor) {
        mBaseInstrumentation.addMonitor(monitor);
    }

    @Override
    public ActivityMonitor addMonitor(IntentFilter filter, ActivityResult result, boolean block) {
        return mBaseInstrumentation.addMonitor(filter, result, block);
    }

    @Override
    public ActivityMonitor addMonitor(String cls, ActivityResult result, boolean block) {
        return mBaseInstrumentation.addMonitor(cls, result, block);
    }

    @Override
    public boolean checkMonitorHit(ActivityMonitor monitor, int minHits) {
        return mBaseInstrumentation.checkMonitorHit(monitor, minHits);
    }

    @Override
    public Activity waitForMonitor(ActivityMonitor monitor) {
        return mBaseInstrumentation.waitForMonitor(monitor);
    }

    @Override
    public Activity waitForMonitorWithTimeout(ActivityMonitor monitor, long timeOut) {
        return mBaseInstrumentation.waitForMonitorWithTimeout(monitor, timeOut);
    }

    @Override
    public void removeMonitor(ActivityMonitor monitor) {
        mBaseInstrumentation.removeMonitor(monitor);
    }

    @Override
    public boolean invokeMenuActionSync(Activity targetActivity, int id, int flag) {
        return mBaseInstrumentation.invokeMenuActionSync(targetActivity, id, flag);
    }

    @Override
    public boolean invokeContextMenuAction(Activity targetActivity, int id, int flag) {
        return mBaseInstrumentation.invokeContextMenuAction(targetActivity, id, flag);
    }

    @Override
    public void sendStringSync(String text) {
        mBaseInstrumentation.sendStringSync(text);
    }

    @Override
    public void sendKeySync(KeyEvent event) {
        mBaseInstrumentation.sendKeySync(event);
    }

    @Override
    public void sendKeyDownUpSync(int key) {
        mBaseInstrumentation.sendKeyDownUpSync(key);
    }

    @Override
    public void sendCharacterSync(int keyCode) {
        mBaseInstrumentation.sendCharacterSync(keyCode);
    }

    @Override
    public void sendPointerSync(MotionEvent event) {
        mBaseInstrumentation.sendPointerSync(event);
    }

    @Override
    public void sendTrackballEventSync(MotionEvent event) {
        mBaseInstrumentation.sendTrackballEventSync(event);
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return mBaseInstrumentation.newApplication(cl, className, context);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        mBaseInstrumentation.callApplicationOnCreate(app);
    }

    @Override
    public Activity newActivity(Class<?> clazz, Context context, IBinder token, Application application, Intent intent, ActivityInfo info, CharSequence title, Activity parent, String id, Object lastNonConfigurationInstance) throws IllegalAccessException, InstantiationException {
        return mBaseInstrumentation.newActivity(clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance);
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return mBaseInstrumentation.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        mBaseInstrumentation.callActivityOnCreate(activity, icicle);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivityCreated(activity, icicle);
        }
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        mBaseInstrumentation.callActivityOnCreate(activity, icicle, persistentState);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivityCreated(activity, icicle);
        }
    }

    @Override
    public void callActivityOnDestroy(Activity activity) {
        mBaseInstrumentation.callActivityOnDestroy(activity);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivityDestroyed(activity);
        }
    }

    @Override
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
        mBaseInstrumentation.callActivityOnRestoreInstanceState(activity, savedInstanceState);
    }

    @Override
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState, PersistableBundle persistentState) {
        mBaseInstrumentation.callActivityOnRestoreInstanceState(activity, savedInstanceState, persistentState);
    }

    @Override
    public void callActivityOnPostCreate(Activity activity, Bundle icicle) {
        mBaseInstrumentation.callActivityOnPostCreate(activity, icicle);
    }

    @Override
    public void callActivityOnPostCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        mBaseInstrumentation.callActivityOnPostCreate(activity, icicle, persistentState);
    }

    @Override
    public void callActivityOnNewIntent(Activity activity, Intent intent) {
        mBaseInstrumentation.callActivityOnNewIntent(activity, intent);
    }

    @Override
    public void callActivityOnStart(Activity activity) {
        mBaseInstrumentation.callActivityOnStart(activity);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivityStarted(activity);
        }
    }

    @Override
    public void callActivityOnRestart(Activity activity) {
        mBaseInstrumentation.callActivityOnRestart(activity);
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        mBaseInstrumentation.callActivityOnResume(activity);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivityResumed(activity);
        }
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        mBaseInstrumentation.callActivityOnStop(activity);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivityStopped(activity);
        }
    }

    @Override
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState) {
        mBaseInstrumentation.callActivityOnSaveInstanceState(activity, outState);
    }

    @Override
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState, PersistableBundle outPersistentState) {
        mBaseInstrumentation.callActivityOnSaveInstanceState(activity, outState, outPersistentState);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivitySaveInstanceState(activity, outState);
        }
    }

    @Override
    public void callActivityOnPause(Activity activity) {
        mBaseInstrumentation.callActivityOnPause(activity);
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.onActivityPaused(activity);
        }
    }

    @Override
    public void callActivityOnUserLeaving(Activity activity) {
        mBaseInstrumentation.callActivityOnUserLeaving(activity);
    }

    @Override
    public void startAllocCounting() {
        mBaseInstrumentation.startAllocCounting();
    }

    @Override
    public void stopAllocCounting() {
        mBaseInstrumentation.stopAllocCounting();
    }

    @Override
    public Bundle getAllocCounts() {
        return mBaseInstrumentation.getAllocCounts();
    }

    @Override
    public Bundle getBinderCounts() {
        return mBaseInstrumentation.getBinderCounts();
    }

    @Override
    public UiAutomation getUiAutomation() {
        return mBaseInstrumentation.getUiAutomation();
    }


    /**
     * Some file managers launch ACTION_VIEW using ClipData-only or EXTRA_STREAM without data/type set.
     * Inside virtualization hooks, data/type may get dropped, which makes resolution fail with
     * ActivityNotFoundException. Normalize the intent so the framework can resolve it.
     */
    private static Intent normalizeViewIntent(Context context, Intent intent) {
        if (intent == null) return null;
        try {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri data = intent.getData();
                if (data == null) {
                    ClipData cd = intent.getClipData();
                    if (cd != null && cd.getItemCount() > 0) {
                        Uri u = cd.getItemAt(0).getUri();
                        if (u != null) data = u;
                    }
                    if (data == null) {
                        try {
                            Object extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                            if (extra instanceof Uri) data = (Uri) extra;
                        } catch (Throwable ignored) {
                        }
                    }
                    if (data != null) {
                        intent.setData(data);
                    }
                }

                if (data != null) {
                    // Ensure type is set if possible (helps resolver match).
                    String type = intent.getType();
                    if (type == null) {
                        try {
                            type = context.getContentResolver().getType(data);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (type != null) {
                        // setDataAndType keeps both consistent
                        intent.setDataAndType(data, type);
                    }

                    // Ensure ClipData exists for UriGrants on newer Android versions.
                    if (intent.getClipData() == null) {
                        try {
                            intent.setClipData(ClipData.newRawUri("data", data));
                        } catch (Throwable ignored) {
                        }
                    }

                    // Always include grant flags when sharing content Uris.
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                }
            }
        } catch (Throwable ignored) {
        }
        return intent;
    }



private static android.content.ActivityNotFoundException findActivityNotFound(Throwable t) {
    Throwable cur = t;
    while (cur != null) {
        if (cur instanceof android.content.ActivityNotFoundException) {
            return (android.content.ActivityNotFoundException) cur;
        }
        cur = cur.getCause();
    }
    return null;
}

private static Intent tryRecoverViewIntent(Intent intent) {
    if (intent == null) return null;
    if (!Intent.ACTION_VIEW.equals(intent.getAction())) return intent;
    if (intent.getData() != null) return intent;

    // Try common extras used by apps when launching a browser/share target.
    String[] keys = new String[] {
            Intent.EXTRA_TEXT,
            Intent.EXTRA_SUBJECT,
            "url",
            "uri",
            "android.intent.extra.URL",
            "android.intent.extra.URI"
    };
    for (String k : keys) {
        try {
            String v = intent.getStringExtra(k);
            if (v == null) continue;
            v = v.trim();
            if (v.isEmpty()) continue;

            // If it looks like a URL but missing scheme, assume https.
            if (v.startsWith("www.")) v = "https://" + v;
            if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("content://") || v.startsWith("file://") || v.startsWith("market://")) {
                intent.setData(android.net.Uri.parse(v));
                return intent;
            }
        } catch (Throwable ignored) {
        }
    }
    return intent;
}

    public ActivityResult execStartActivity(Context context, IBinder binder, IBinder binder1, Activity activity, Intent intent, int i, Bundle bundle) throws Throwable {
        Intent fixedIntent = normalizeViewIntent(context, intent);
        try {
            return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                Activity.class,
                Intent.class,
                Integer.TYPE,
                Bundle.class).callByCaller(mBaseInstrumentation, new Object[]{context, binder, binder1, activity, fixedIntent, i, bundle});
        } catch (Throwable t) {
            android.content.ActivityNotFoundException anf = findActivityNotFound(t);
            if (anf != null) {
                // Recover missing VIEW Uri from extras if possible, then retry.
                tryRecoverViewIntent(fixedIntent);
                try {
                    return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                Activity.class,
                Intent.class,
                Integer.TYPE,
                Bundle.class).callByCaller(mBaseInstrumentation, new Object[]{context, binder, binder1, activity, fixedIntent, i, bundle});
                } catch (Throwable ignored) {
                }
                // Last resort: don't crash the guest; just no-op.
                return null;
            }
            throw t;
        }
    }

    public ActivityResult execStartActivity(Context context, IBinder binder, IBinder binder1, String str, Intent intent, int i, Bundle bundle) throws Throwable {
        Intent fixedIntent = normalizeViewIntent(context, intent);
        return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                String.class,
                Intent.class,
                Integer.TYPE,
                Bundle.class).callByCaller(mBaseInstrumentation, new Object[]{context, binder, binder1, str, fixedIntent, i, bundle});
    }

    public ActivityResult execStartActivity(Context context, IBinder binder, IBinder binder1, Fragment fragment, Intent intent, int i) throws Throwable {
        Intent fixedIntent = normalizeViewIntent(context, intent);
        try {
            return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                Fragment.class,
                Intent.class,
                Integer.TYPE).callByCaller(mBaseInstrumentation, new Object[]{context, binder, binder1, fragment, fixedIntent, i});
        } catch (Throwable t) {
            android.content.ActivityNotFoundException anf = findActivityNotFound(t);
            if (anf != null) {
                // Recover missing VIEW Uri from extras if possible, then retry.
                tryRecoverViewIntent(fixedIntent);
                try {
                    return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                Fragment.class,
                Intent.class,
                Integer.TYPE).callByCaller(mBaseInstrumentation, new Object[]{context, binder, binder1, fragment, fixedIntent, i});
                } catch (Throwable ignored) {
                }
                // Last resort: don't crash the guest; just no-op.
                return null;
            }
            throw t;
        }
    }

    public ActivityResult execStartActivity(Context context, IBinder binder, IBinder binder1, Activity activity, Intent intent, int i) throws Throwable {
        Intent fixedIntent = normalizeViewIntent(context, intent);
        return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                Activity.class,
                Intent.class,
                Integer.TYPE).callByCaller(mBaseInstrumentation, new Object[]{context, binder, binder1, activity, fixedIntent, i});
    }

    public ActivityResult execStartActivity(Context context, IBinder binder, IBinder binder1, Fragment fragment, Intent intent, int i, Bundle bundle) throws Throwable {
        Intent fixedIntent = normalizeViewIntent(context, intent);
        return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                Fragment.class,
                Intent.class,
                Integer.TYPE,
                Bundle.class).callByCaller(mBaseInstrumentation, new Object[]{context, binder, binder1, fragment, fixedIntent, i, bundle});
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public ActivityResult execStartActivity(Context context, IBinder iBinder, IBinder iBinder2, Activity activity, Intent intent, int i, Bundle bundle, UserHandle userHandle) throws Throwable {
        Intent fixedIntent = normalizeViewIntent(context, intent);
        return invokeExecStartActivity(mBaseInstrumentation,
                Context.class,
                IBinder.class,
                IBinder.class,
                Activity.class,
                Intent.class,
                Integer.TYPE,
                Bundle.class,
                UserHandle.class).callByCaller(mBaseInstrumentation, new Object[]{context, iBinder, iBinder2, activity, fixedIntent, i, bundle, userHandle});
    }

    private static Reflector invokeExecStartActivity(Object obj, Class<?>... args) throws NoSuchMethodException {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                return Reflector.on(obj.getClass())
                        .method("execStartActivity", args);
            } catch (Exception e) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchMethodException();
    }
}
