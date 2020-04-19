package dev.hihi.virtualmobilevrheadset;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

public class PhoneActivity extends Activity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "MainActivity";
    public static final String SP_NAME = "settings";
    public static final String LAST_IP = "last_ip";

    private MyTextureView mTextureView;
    private ViewGroup mServerInfoLayout;

    private MirrorEngine mMirrorEngine = new MirrorEngine();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);
        mTextureView = (MyTextureView) findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(this);
        mServerInfoLayout = findViewById(R.id.server_info_layout);

        findViewById(R.id.connect_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.w(TAG, "onclick");
                if (mMirrorEngine != null) {
                    boolean running = mMirrorEngine.isRunning();
                    if (running) {
                        mMirrorEngine.stopClient();
                    } else {
                        tryStartStreaming();
                    }
                    updateUI();
                }
            }
        });

        restoreLastIp();
    }

    private void tryStartStreaming() {
        String ip = ((EditText) findViewById(R.id.server_ip)).getText().toString();
        if (TextUtils.isEmpty(ip)) {
            return;
        }
        saveLastIp(ip);

        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture == null) {
            return;
        }

        mMirrorEngine.startClient(ip, false, mOnSizeChangeCallback, new Surface(surfaceTexture), mTextureView);
    }

    private VideoDecoder.OnSizeChangeCallback mOnSizeChangeCallback = new VideoDecoder.OnSizeChangeCallback() {
        @Override
        public void onChange(int width, int height, boolean isRotated) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float screenProportion = (float) displayMetrics.widthPixels / (float) displayMetrics.heightPixels;

            float videoProportion = (float) width / (float) height;

            final ViewGroup.LayoutParams lp = mTextureView.getLayoutParams();
            if (videoProportion > screenProportion) {
                lp.width = displayMetrics.widthPixels;
                lp.height = (int) ((float) displayMetrics.widthPixels / videoProportion);
            } else {
                lp.width = (int) (videoProportion * (float) displayMetrics.heightPixels);
                lp.height = displayMetrics.heightPixels;
            }

            mTextureView.post(new Runnable() {
                @Override
                public void run() {
                    mTextureView.setLayoutParams(lp);
                }
            });
            mTextureView.setVideoSourceSize(width, height, isRotated);
        }
    };

    private void restoreLastIp() {
        SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String ip = sp.getString(LAST_IP, null);
        if (!TextUtils.isEmpty(ip)) {
            ((EditText) findViewById(R.id.server_ip)).setText(ip);
        }
    }

    private void saveLastIp(String ip) {
        SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(LAST_IP, ip).apply();
    }

    private void updateUI() {
        if (mMirrorEngine == null) {
            return;
        }
        boolean running = mMirrorEngine.isRunning();
        Button button = findViewById(R.id.connect_btn);
        if (running) {
            button.setText("Disconnect");
            mServerInfoLayout.setVisibility(View.GONE);
            mTextureView.setVisibility(View.VISIBLE);
        } else {
            button.setText("Connect");
            mServerInfoLayout.setVisibility(View.VISIBLE);
            // mTextureView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
        if (mMirrorEngine != null) {
            tryStartStreaming();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMirrorEngine != null) {
            mMirrorEngine.stopClient();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }
}
