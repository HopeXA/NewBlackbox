package top.niunaijun.blackbox.fake.service;

import android.Manifest;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;
import black.android.os.BRServiceManager;
import black.android.permission.BRIPermissionManagerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.service.base.PkgMethodProxy;
import top.niunaijun.blackbox.fake.service.base.ValueMethodProxy;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class IPermissionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IPermissionManagerProxy";

    private static final String P = "permissionmgr";

    public IPermissionManagerProxy() {
        super(BRServiceManager.get().getService(P));
    }

    @Override
    protected Object getWho() {
        return BRIPermissionManagerStub.get().asInterface(BRServiceManager.get().getService(P));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("permissionmgr");
        BRActivityThread.getWithException()._set_sPermissionManager(proxyInvocation);
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ValueMethodProxy("addPermission", true));
        addMethodHook(new ValueMethodProxy("performDexOpt", true));
        addMethodHook(new ValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("checkDeviceIdentifierAccess", false));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
    }

    private static boolean isNetworkPermission(String permission) {
        return Manifest.permission.INTERNET.equals(permission)
                || Manifest.permission.ACCESS_NETWORK_STATE.equals(permission)
                || Manifest.permission.ACCESS_WIFI_STATE.equals(permission)
                || Manifest.permission.CHANGE_NETWORK_STATE.equals(permission)
                || Manifest.permission.CHANGE_WIFI_STATE.equals(permission);
    }

    @ProxyMethod("checkPermission")
    public static class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String appPkg = BActivityThread.getAppPackageName();
            String permName = null;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof String) {
                        String s = (String) args[i];
                        if (s.contains(".permission.") || s.startsWith("android.permission")) {
                            permName = s;
                        } else if (appPkg != null && appPkg.equals(s)) {
                            args[i] = BlackBoxCore.getHostPkg();
                        }
                    }
                }
            }
            try {
                Object result = method.invoke(who, args);
                if (result instanceof Integer && (Integer) result == PackageManager.PERMISSION_GRANTED) {
                    return result;
                }
                if (isNetworkPermission(permName) && hasHostPermission(permName)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                return result;
            } catch (Throwable t) {
                if (isNetworkPermission(permName) && hasHostPermission(permName)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                throw t;
            }
        }
    }

    @ProxyMethod("checkUidPermission")
    public static class CheckUidPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permName = null;
            int bUid = BActivityThread.getBUid();
            int vUid = BActivityThread.getUid();
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof String) {
                        permName = (String) args[i];
                    } else if (args[i] instanceof Integer) {
                        int val = (Integer) args[i];
                        if (val >= 10000 && (val == bUid || val == vUid)) {
                            args[i] = BlackBoxCore.getHostUid();
                        }
                    }
                }
            }
            try {
                Object result = method.invoke(who, args);
                if (result instanceof Integer && (Integer) result == PackageManager.PERMISSION_GRANTED) {
                    return result;
                }
                if (isNetworkPermission(permName) && hasHostPermission(permName)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                return result;
            } catch (Throwable t) {
                if (isNetworkPermission(permName) && hasHostPermission(permName)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                throw t;
            }
        }
    }

    private static boolean hasHostPermission(String permission) {
        if (permission == null || BlackBoxCore.getContext() == null) return false;
        try {
            return BlackBoxCore.getContext().checkPermission(
                    permission, android.os.Process.myPid(), BlackBoxCore.getHostUid())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
