package app.pomee4.waze;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically discovers installed music/audio apps exposing Android's standard
 * {@code MediaBrowserService} and registers them into Waze's audio partner list.
 */
public final class CustomAudioApps {
    private CustomAudioApps() {}

    private static final String TAG = "WazeAudioPatches";
    private static final String ACTION_MEDIA_BROWSER = "android.media.browse.MediaBrowserService";
    private static final String ACTION_WAZE_INIT = "com.waze.sdk.audio.ACTION_INIT";
    private static final String SDK_CONFIG_CLASS = "com.waze.sdk.SdkConfiguration";
    /** Audio partner scope flag (SdkConfiguration.FEATURE_AUDIO). */
    private static final int SCOPE_AUDIO = 0x200000;

    private static volatile Context appContext;
    private static final Set<String> customPackages = ConcurrentHashMap.newKeySet();

    public static void init(Application app) {
        if (app == null) return;
        appContext = app.getApplicationContext();
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    refreshPartnerList();
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                app.registerReceiver(receiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register package change receiver", t);
        }
    }

    private static void refreshPartnerList() {
        try {
            Class<?> cls = Class.forName(SDK_CONFIG_CLASS);
            Method m = cls.getDeclaredMethod("updateAppList");
            m.setAccessible(true);
            m.invoke(null);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to refresh Waze audio partner list", t);
        }
    }

    public static void populateAppConfigs(Map<String, Object> appConfigs, Class<?> configClass) {
        Context ctx = appContext;
        if (ctx == null || appConfigs == null || configClass == null) return;

        try {
            PackageManager pm = ctx.getPackageManager();
            String selfPkg = ctx.getPackageName();

            Set<String> nativePartners = new HashSet<>();
            List<ResolveInfo> initReceivers = pm.queryBroadcastReceivers(
                new Intent(ACTION_WAZE_INIT),
                PackageManager.MATCH_ALL
            );
            if (initReceivers != null) {
                for (ResolveInfo ri : initReceivers) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        nativePartners.add(ri.activityInfo.packageName);
                    }
                }
            }

            Constructor<?> ctor = configClass.getDeclaredConstructor(
                String.class,
                String.class,
                int.class,
                String.class,
                byte[].class
            );
            ctor.setAccessible(true);

            Set<String> discovered = new HashSet<>();
            List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(ACTION_MEDIA_BROWSER),
                PackageManager.MATCH_ALL
            );
            if (services != null) {
                for (ResolveInfo ri : services) {
                    ServiceInfo si = ri.serviceInfo;
                    if (si == null || si.packageName == null) continue;
                    String pkg = si.packageName;

                    if (pkg.equals(selfPkg)
                        || discovered.contains(pkg)
                        || appConfigs.containsKey(pkg)
                        || nativePartners.contains(pkg)) {
                        continue;
                    }
                    if (pm.getLaunchIntentForPackage(pkg) == null) {
                        continue;
                    }

                    String label = resolveLabel(pm, si.applicationInfo, pkg);
                    Object cfg = ctor.newInstance(pkg, label, SCOPE_AUDIO, null, null);
                    appConfigs.put(pkg, cfg);
                    discovered.add(pkg);
                }
            }

            customPackages.clear();
            customPackages.addAll(discovered);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to discover MediaBrowserService apps", t);
        }
    }

    private static String resolveLabel(PackageManager pm, ApplicationInfo info, String pkg) {
        if (info != null) {
            CharSequence cs = pm.getApplicationLabel(info);
            if (cs != null) {
                String s = cs.toString().trim();
                if (!s.isEmpty() && !s.equals(pkg)) {
                    return s;
                }
            }
        }
        int dot = pkg.lastIndexOf('.');
        return (dot >= 0 && dot + 1 < pkg.length()) ? pkg.substring(dot + 1) : pkg;
    }

    public static boolean isCustomAudioApp(String packageName) {
        return packageName != null && customPackages.contains(packageName);
    }
}
