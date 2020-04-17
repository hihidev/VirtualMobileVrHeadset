package dev.hihi.virtualmobilevrheadset;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "MainActivity";
    private static final String SP_NAME = "settings";
    private static final String LAST_IP = "last_ip";

    private final static int AUDIO_PORT = 1235;
    private final static int VIDEO_PORT = 1234;
    private final static int COMMAND_PORT = 1236;

    private boolean mIsRunning = false;

    private AudioDecoder mAudioDecoder = null;
    private VideoDecoder mVideoDecoder = null;

    private MirrorClientInterface mAudioClient = null;
    private MirrorClientInterface mVideoClient = null;
    private MirrorClientInterface mCommandClient = null;

    private MyTextureView mTextureView;
    private ViewGroup mServerInfoLayout;

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
                if (mIsRunning) {
                    stopClient();
                } else {
                    Log.w(TAG, "onclick 2");
                    SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
                    if (surfaceTexture != null) {
                        Log.w(TAG, "onclick 3");
                        startClient(surfaceTexture);
                    }
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

    private void startClient(SurfaceTexture surfaceTexture) {
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
        updateUI();
        startAudioMirror(ip);
        startVideoMirror(ip, (MyTextureView) findViewById(R.id.textureView), new Surface(surfaceTexture));
        startCommandClient(ip);
    }

    private void updateUI() {
        Log.w(TAG, "updateUI: " + mIsRunning);
        Button button = findViewById(R.id.connect_btn);
        if (mIsRunning) {
            button.setText("Disconnect");
            mServerInfoLayout.setVisibility(View.GONE);
            mTextureView.setVisibility(View.VISIBLE);
        } else {
            button.setText("Connect");
            mServerInfoLayout.setVisibility(View.VISIBLE);
            // mTextureView.setVisibility(View.INVISIBLE);
        }
    }

    private void stopClient() {
        if (!mIsRunning) {
            Log.w(TAG, "Client is not running");
            return;
        }
        mIsRunning = false;
        updateUI();
        AudioDecoder audioDecoder = mAudioDecoder;
        if (audioDecoder != null) {
            audioDecoder.stop();
        }
        MirrorClientInterface audioClient = mAudioClient;
        if (audioClient != null) {
            audioClient.stop();
        }
        VideoDecoder videoDecoder = mVideoDecoder;
        if (videoDecoder != null) {
            videoDecoder.stop();
        }
        MirrorClientInterface videoClient = mVideoClient;
        if (videoClient != null) {
            videoClient.stop();
        }
    }

    private void startAudioMirror(final String ip) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
                    mAudioClient = new TcpClient();
                    mAudioClient.start("AudioClient", ip, AUDIO_PORT, true);
                    mAudioDecoder = new AudioDecoder();
                    mAudioDecoder.startDecoder(mAudioClient);
                    mAudioClient.waitUntilStopped();
                    mAudioClient.stop();
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

    public void startVideoMirror(final String ip, final MyTextureView view, final Surface surface) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
                    mVideoClient = new TcpClient();
                    mVideoClient.start("VideoClient", ip, VIDEO_PORT, true);
                    mVideoDecoder = new VideoDecoder();
                    mVideoDecoder.startDecoder(getWindowManager(), view, surface, mVideoClient);
                    mVideoClient.waitUntilStopped();
                    Log.i(TAG, "Video client stopped, waiting video decoder to stop");
                    mVideoClient.stop();
                    mVideoDecoder.stop();
                    mVideoDecoder.waitUntilStopped();
                    Log.i(TAG, "TCP client stopped");
                    mVideoDecoder = null;
                    mVideoClient = null;
                }
            }
        }.start();
    }

    public void startCommandClient(final String ip) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
                    mCommandClient = new TcpClient();
                    mTextureView.attachCommandClient(mCommandClient);
                    mCommandClient.start("CommandClient", ip, COMMAND_PORT, false);
                    mCommandClient.waitUntilStopped();
                    mTextureView.removeCommandClient();
                }
            }
        }.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture != null) {
            startClient(surfaceTexture);
        }
        updateUI();
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
