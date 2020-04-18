package dev.hihi.virtualmobilevrheadset;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent();
        if ("Oculus".equals(Build.MANUFACTURER)) {
            intent.setClass(this, VrActivity.class);
        } else {
            intent.setClass(this, PhoneActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
