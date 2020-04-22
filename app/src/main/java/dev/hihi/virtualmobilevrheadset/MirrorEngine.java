package dev.hihi.virtualmobilevrheadset;

import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

public class MirrorEngine {

    private static final String TAG = "MirrorEngine";

    private final static int AUDIO_PORT = 1235;
    private final static int VIDEO_PORT = 1234;
    private final static int COMMAND_PORT = 1236;

    private boolean mIsRunning = false;

    private AudioDecoder mAudioDecoder = null;
    private VideoDecoder mVideoDecoder = null;

    private MirrorClientInterface mAudioClient = null;
    private MirrorClientInterface mVideoClient = null;
    private MirrorClientInterface mCommandClient = null;

    private NsdHelper mNsdHelper = null;
    private String mDiscoveredIp = null;

    public interface TouchSurfaceInterface {
        void attachCommandClient(MirrorClientInterface client);
        void removeCommandClient();
    }

    public void startDiscover(Context context, final Runnable runnable) {
        synchronized (this) {
            if (mNsdHelper == null) {
                mNsdHelper = new NsdHelper(context, new Runnable() {
                    @Override
                    public void run() {
                        updateDiscoveredIp();
                        runnable.run();
                    }
                });
            }
        }
        mNsdHelper.discoverServices();
    }

    public String getDiscoveredIp() {
        return mDiscoveredIp;
    }

    public void updateDiscoveredIp() {
        if (mNsdHelper == null) {
            return;
        }
        NsdServiceInfo info = mNsdHelper.getChosenServiceInfo();
        if (info == null) {
            return;
        }
        mDiscoveredIp = info.getHost().getHostAddress();
    }

    public synchronized void startClient(final String ip, final boolean isLandscapeScreen,
            final VideoDecoder.OnSizeChangeCallback onSizeChangeCallback, final Surface surface,
            final TouchSurfaceInterface touchSurfaceInterface) {
        if (mIsRunning) {
            Log.i(TAG, "Cannot start client, already running");
            return;
        }
        Log.i(TAG, "startClient()");
        mIsRunning = true;
        startAudioMirror(ip);
        startVideoMirror(ip, isLandscapeScreen, onSizeChangeCallback, surface);
        if (touchSurfaceInterface != null) {
            startCommandClient(ip, touchSurfaceInterface);
        }
    }

    public synchronized void stopClient() {
        if (!mIsRunning) {
            Log.i(TAG, "Cannot stop client, already stopped");
            return;
        }
        Log.i(TAG, "stopClient()");
        mIsRunning = false;
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
        MirrorClientInterface commandClient = mCommandClient;
        if (commandClient != null) {
            commandClient.stop();
        }
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    private void startAudioMirror(final String originalIp) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
                    String ip = mDiscoveredIp != null ? mDiscoveredIp : originalIp;
                    if (ip == null) {
                        SystemClock.sleep(500);
                        continue;
                    }
                    mAudioClient = new Tcp("AudioClient", false);
                    mAudioDecoder = new AudioDecoder();

                    mAudioClient.start(ip, AUDIO_PORT, null, null, true);
                    mAudioDecoder.startDecoder(mAudioClient);

                    mAudioClient.waitUntilStopped();

                    mAudioClient.stop();
                    mAudioDecoder.stop();

                    Log.i(TAG, "Audio client stopped, waiting audio decoder to stop");
                    mAudioDecoder.waitUntilStopped();
                    mAudioClient.waitUntilStopped();

                    Log.i(TAG, "Audio decoder stopped");
                    mAudioClient = null;
                    mAudioDecoder = null;
                }
            }
        }.start();
    }

    private void startVideoMirror(final String originalIp, final boolean isLandscapeScreen,
            final VideoDecoder.OnSizeChangeCallback onSizeChangeCallback, final Surface surface) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
                    String ip = mDiscoveredIp != null ? mDiscoveredIp : originalIp;
                    if (ip == null) {
                        SystemClock.sleep(500);
                        continue;
                    }
                    mVideoClient = new Tcp("VideoClient", false);
                    mVideoDecoder = new VideoDecoder();

                    mVideoClient.start(ip, VIDEO_PORT, null, null, true);
                    mVideoDecoder.startDecoder(onSizeChangeCallback, surface, isLandscapeScreen, mVideoClient);

                    mVideoClient.waitUntilStopped();

                    Log.i(TAG, "Video client stopped, waiting video decoder to stop");
                    mVideoClient.stop();
                    mVideoDecoder.stop();

                    mVideoDecoder.waitUntilStopped();
                    mVideoClient.waitUntilStopped();

                    Log.i(TAG, "TCP client stopped");
                    mVideoDecoder = null;
                    mVideoClient = null;
                }
            }
        }.start();
    }

    private void startCommandClient(final String originalIp, final TouchSurfaceInterface touchSurfaceInterface) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
                    String ip = mDiscoveredIp != null ? mDiscoveredIp : originalIp;
                    if (ip == null) {
                        SystemClock.sleep(500);
                        continue;
                    }
                    mCommandClient = new Tcp("CommandClient", false);
                    touchSurfaceInterface.attachCommandClient(mCommandClient);
                    mCommandClient.start(ip, COMMAND_PORT, null, null, false);

                    mCommandClient.waitUntilStopped();

                    mCommandClient.stop();
                    mCommandClient.waitUntilStopped();

                    touchSurfaceInterface.removeCommandClient();
                    mCommandClient = null;
                }
            }
        }.start();
    }
}
