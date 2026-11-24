package top.niunaijun.blackbox.fake.delegate;

import android.app.IBinderSession;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import black.android.app.BRIServiceConnectionO;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private static volatile Class<?> sCachedConnClass;
    private static volatile Method sConnected4Method;
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;

    private ServiceConnectionDelegate(IServiceConnection mConn, ComponentName targetComponent) {
        this.mConn = mConn;
        this.mComponentName = targetComponent;
    }

    public static ServiceConnectionDelegate getDelegate(IBinder iBinder) {
        return sServiceConnectDelegate.get(iBinder);
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent) {
        final IBinder iBinder = base.asBinder();
        ServiceConnectionDelegate delegate = sServiceConnectDelegate.get(iBinder);
        if (delegate == null) {
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        sServiceConnectDelegate.remove(iBinder);
                        iBinder.unlinkToDeath(this, 0);
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            delegate = new ServiceConnectionDelegate(base, intent.getComponent());
            sServiceConnectDelegate.put(iBinder, delegate);
        }
        return delegate;
    }

    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        connected(name, service, false);
    }

    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        dispatchConnected(name, service, null, dead);
    }

    public void connected(ComponentName name, IBinder service, IBinderSession session, boolean dead) throws RemoteException {
        dispatchConnected(name, service, session, dead);
    }

    private void dispatchConnected(ComponentName name, IBinder service, IBinderSession session, boolean dead) throws RemoteException {
        ComponentName targetComponent = mComponentName != null ? mComponentName : name;
        if (BuildCompat.isOreo()) {
            Method connected4 = getConnected4Method(mConn.getClass());
            if (connected4 != null) {
                try {
                    connected4.invoke(mConn, targetComponent, service, session, dead);
                    return;
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RemoteException) {
                        throw (RemoteException) cause;
                    }
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    throw new RuntimeException(cause != null ? cause : e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            BRIServiceConnectionO.get(mConn).connected(targetComponent, service, dead);
        } else {
            mConn.connected(targetComponent, service);
        }
    }

    private static Method getConnected4Method(Class<?> connClass) {
        if (sCachedConnClass == connClass) {
            return sConnected4Method;
        }
        synchronized (ServiceConnectionDelegate.class) {
            if (sCachedConnClass == connClass) {
                return sConnected4Method;
            }
            Method found = null;
            try {
                found = connClass.getMethod("connected", ComponentName.class, IBinder.class, IBinderSession.class, boolean.class);
                found.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
                try {
                    found = connClass.getDeclaredMethod("connected", ComponentName.class, IBinder.class, IBinderSession.class, boolean.class);
                    found.setAccessible(true);
                } catch (NoSuchMethodException ignored2) {
                    found = null;
                }
            }
            sConnected4Method = found;
            sCachedConnClass = connClass;
            return found;
        }
    }
}
