package dev.hihi.virtualmobilevrheadset;

import android.app.Application;

public class MyApplication extends Application {

    private static Application sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    public static Application getApplication() {
        return sInstance;
    }
}
