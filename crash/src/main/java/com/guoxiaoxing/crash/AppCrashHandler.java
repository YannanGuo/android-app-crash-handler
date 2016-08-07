package com.guoxiaoxing.crash;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Author: guoxiaoxing
 * Date: 16/8/7 下午4:36
 * Function: app crash handler
 * <p>
 * For more information, you can visit https://github.com/guoxiaoxing or contact me by
 * guoxiaoxingv@163.com
 */
public final class AppCrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = AppCrashHandler.class.getSimpleName();
    private static final String EXTRA_RESTART_ACTIVITY_CLASS = "restart_activity_class";
    private static final String EXTRA_STACK_TRACE = "stack_track";
    private static final int MAX_STACK_TRACE_SIZE = 131071; //128 KB - 1
    private volatile static AppCrashHandler mAppCrashHandler;
    private static Application mAppContext;
    private static Class<? extends Activity> errorActivityClass = null;
    private static Class<? extends Activity> restartActivityClass = null;
    private static WeakReference<Activity> lastActivityCreated = new WeakReference<>(null);
    private static boolean isInBackground = false;

    private AppCrashHandler(Context context) {
        mAppContext = (Application) context.getApplicationContext();
    }

    private static AppCrashHandler getInstance(Context context) {
        if (mAppCrashHandler == null) {
            synchronized (AppCrashHandler.class) {
                if (mAppCrashHandler == null) {
                    mAppCrashHandler = new AppCrashHandler(context);
                }
            }
        }
        return mAppCrashHandler;
    }

    public static void setupHandler(Context context) {
        Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (oldHandler != null && oldHandler.getClass().getName().startsWith(BuildConfig.APPLICATION_ID)) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(getInstance(context));
        registerAppLifecycle();
    }

    /**
     * Given an Intent, returns the restart activity class extra from it.
     *
     * @param intent The Intent. Must not be null.
     * @return The restart activity class, or null if not provided.
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends Activity> getRestartActivityClassFromIntent(Intent intent) {
        Serializable serializedClass = intent.getSerializableExtra(AppCrashHandler.EXTRA_RESTART_ACTIVITY_CLASS);

        if (serializedClass != null && serializedClass instanceof Class) {
            return (Class<? extends Activity>) serializedClass;
        } else {
            return null;
        }
    }

    /**
     * Given an Intent, returns several error details including the stack trace extra from the intent.
     *
     * @param context A valid context. Must not be null.
     * @param intent  The Intent. Must not be null.
     * @return The full error details.
     */
    public static String getAllErrorDetailsFromIntent(Context context, Intent intent) {

        Date currentDate = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

        //Get build date
        String buildDateAsString = getBuildDateAsString(context, dateFormat);

        StringBuilder sb = new StringBuilder();

        sb.append("Build version: ").append(BuildConfig.VERSION_NAME).append(" \n")
                .append("Build code: ").append(BuildConfig.VERSION_CODE).append(" \n")
                .append("Build date: ").append(buildDateAsString).append(" \n")
                .append("Current date: ").append(dateFormat.format(currentDate)).append(" \n")
                .append("Device: ").append(getDeviceModelName()).append(" \n")
                .append("Android version：").append(Build.VERSION.RELEASE).append(" \n\n")
                .append("Stack trace:  \n")
                .append(getStackTraceFromIntent(intent));

        return sb.toString();
    }

    /**
     * Given an Intent, returns the stack trace extra from it.
     *
     * @param intent The Intent. Must not be null.
     * @return The stacktrace, or null if not provided.
     */
    public static String getStackTraceFromIntent(Intent intent) {
        return intent.getStringExtra(AppCrashHandler.EXTRA_STACK_TRACE);
    }

    /**
     * INTERNAL method that checks if the stack trace that just crashed is conflictive. This is true in the following scenarios:
     * - The application has crashed while initializing (handleBindApplication is in the stack)
     * - The error activity has crashed (activityClass is in the stack)
     *
     * @param throwable     The throwable from which the stack trace will be checked
     * @param activityClass The activity class to launch when the app crashes
     * @return true if this stack trace is conflictive and the activity must not be launched, false otherwise
     */
    private static boolean isStackTraceLikelyConflictive(Throwable throwable, Class<? extends Activity> activityClass) {
        do {
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if ((element.getClassName().equals("android.app.ActivityThread") && element.getMethodName().equals("handleBindApplication")) || element.getClassName().equals(activityClass.getName())) {
                    return true;
                }
            }
        } while ((throwable = throwable.getCause()) != null);
        return false;
    }

    /// INTERNAL METHODS NOT TO BE USED BY THIRD PARTIES

    /**
     * INTERNAL method that returns the build date of the current APK as a string, or null if unable to determine it.
     *
     * @param context    A valid context. Must not be null.
     * @param dateFormat DateFormat to use to convert from Date to String
     * @return The formatted date, or "Unknown" if unable to determine it.
     */
    private static String getBuildDateAsString(Context context, DateFormat dateFormat) {
        String buildDate;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            ZipFile zf = new ZipFile(ai.sourceDir);
            ZipEntry ze = zf.getEntry("classes.dex");
            long time = ze.getTime();
            buildDate = dateFormat.format(new Date(time));
            zf.close();
        } catch (Exception e) {
            buildDate = "Unknown";
        }
        return buildDate;
    }

    /**
     * INTERNAL method that returns the device model name with correct capitalization.
     * Taken from: http://stackoverflow.com/a/12707479/1254846
     *
     * @return The device model name (i.e., "LGE Nexus 5")
     */
    public static String getDeviceModelName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private static void registerAppLifecycle() {
        mAppContext.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private int currentlyStartedActivities = 0;

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity.getClass() != errorActivityClass) {
                    // Copied from ACRA:
                    // Ignore activityClass because we want the last
                    // application Activity that was started so that we can
                    // explicitly kill it off.
                    lastActivityCreated = new WeakReference<>(activity);
                    Log.d(TAG, activity.getClass().getName());
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                currentlyStartedActivities++;
                isInBackground = (currentlyStartedActivities == 0);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                //Do nothing
            }

            @Override
            public void onActivityPaused(Activity activity) {
                //Do nothing
            }

            @Override
            public void onActivityStopped(Activity activity) {
                //Do nothing
                currentlyStartedActivities--;
                isInBackground = (currentlyStartedActivities == 0);
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                //Do nothing
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                //Do nothing
            }
        });
    }

    /**
     * INTERNAL method that kills the current process.
     * It is used after restarting or killing the app.
     */
    private static void killCurrentProcess() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    @Override
    public void uncaughtException(Thread thread, final Throwable throwable) {

        if (errorActivityClass == null) {
            errorActivityClass = AppCrashActivity.class;
        }

        if (isStackTraceLikelyConflictive(throwable, errorActivityClass)) {
            Log.e(TAG, "Your application class or your error activity have crashed, the custom activity will not be launched!");
        } else {
            if (isInBackground) {
                return;
            }
            final Intent intent = new Intent(mAppContext, errorActivityClass);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTraceString = sw.toString();

            //Reduce data to 128KB so we don't get a TransactionTooLargeException when sending the intent.
            //The limit is 1MB on Android but some devices seem to have it lower.
            //See: http://developer.android.com/reference/android/os/TransactionTooLargeException.html
            //And: http://stackoverflow.com/questions/11451393/what-to-do-on-transactiontoolargeexception#comment46697371_12809171
            if (stackTraceString.length() > MAX_STACK_TRACE_SIZE) {
                String disclaimer = " [stack trace too large]";
                stackTraceString = stackTraceString.substring(0, MAX_STACK_TRACE_SIZE - disclaimer.length()) + disclaimer;
            }

            if (restartActivityClass == null) {
                //返回应用首页
                restartActivityClass = MainActivity.class;
            }

            Log.e(TAG, stackTraceString);

            intent.putExtra(EXTRA_STACK_TRACE, stackTraceString);
            intent.putExtra(EXTRA_RESTART_ACTIVITY_CLASS, restartActivityClass);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mAppContext.startActivity(intent);

            final Activity lastActivity = lastActivityCreated.get();
            if (lastActivity != null) {
                //We finish the activity, this solves a bug which causes infinite recursion.
                //This is unsolvable in API<14, so beware!
                //See: https://github.com/ACRA/acra/issues/42
                lastActivity.finish();
                lastActivityCreated.clear();
            }
            killCurrentProcess();
        }
    }

    /**
     * INTERNAL method that capitalizes the first character of a string
     *
     * @param s The string to capitalize
     * @return The capitalized string
     */
    private static String capitalize(String s) {
        if (TextUtils.isEmpty(s)) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
}