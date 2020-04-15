package dev.hihi.virtualmobilevrheadset;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "MainActivity";
    private static final String SP_NAME = "settings";
    private static final String LAST_IP = "last_ip";

    private final static int AUDIO_PORT = 1235;
    private final static int SCREEN_PORT = 1234;

    private boolean mIsRunning = false;

    private AudioDecoder mAudioDecoder = null;
    private MirrorClientInterface mAudioClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);
        TextureView textureView = (TextureView) findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(this);

        findViewById(R.id.connect_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsRunning) {
                    stopClient();
                } else {
                    startClient();
                }
            }
        });

        restoreLastIp();
    }

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

    private void startClient() {
        if (mIsRunning) {
            Log.w(TAG, "Client is already running");
            return;
        }
        String ip = ((EditText) findViewById(R.id.server_ip)).getText().toString();
        if (TextUtils.isEmpty(ip)) {
            return;
        }
        saveLastIp(ip);
        mIsRunning = true;
        updateConnectBtn();
        startAudioMirror(ip);
    }

    private void updateConnectBtn() {
        Button button = findViewById(R.id.connect_btn);
        if (mIsRunning) {
            button.setText("Disconnect");
        } else {
            button.setText("Connect");
        }
    }

    private void stopClient() {
        if (!mIsRunning) {
            Log.w(TAG, "Client is not running");
            return;
        }
        mIsRunning = false;
        updateConnectBtn();
        AudioDecoder decoder = mAudioDecoder;
        if (decoder != null) {
            decoder.stop();
        }
    }

    private void startAudioMirror(final String ip) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
                    mAudioClient = new TcpClient();
                    mAudioClient.start(ip, AUDIO_PORT);
                    mAudioDecoder = new AudioDecoder();
                    mAudioDecoder.startDecoder(mAudioClient);
                    mAudioClient.waitUntilStopped();
                    mAudioDecoder.stop();
                    Log.i(TAG, "Audio client stopped, waiting audio decoder to stop");
                    mAudioDecoder.waitUntilStopped();
                    Log.i(TAG, "Audio decoder stopped");
                    mAudioClient = null;
                    mAudioDecoder = null;
                }
            }
        }.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        startClient();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopClient();
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
