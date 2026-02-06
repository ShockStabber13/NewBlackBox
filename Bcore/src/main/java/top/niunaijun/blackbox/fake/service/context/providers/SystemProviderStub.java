package top.niunaijun.blackbox.fake.service.context.providers;

import android.os.IInterface;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;

public class SystemProviderStub extends ClassInvocationStub implements BContentProvider {
    public static final String TAG = "SystemProviderStub";
    private IInterface mBase;
    @SuppressWarnings("unused")
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
        // no-op
    }

    @Override
    protected void onBindMethod() {
        // no-op
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }
        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            throw cause;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
