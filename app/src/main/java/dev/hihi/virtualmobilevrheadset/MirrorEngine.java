package dev.hihi.virtualmobilevrheadset;

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

    public interface TouchSurfaceInterface {
        void attachCommandClient(MirrorClientInterface client);
        void removeCommandClient();
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

    private void startAudioMirror(final String ip) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
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

    private void startVideoMirror(final String ip, final boolean isLandscapeScreen,
            final VideoDecoder.OnSizeChangeCallback onSizeChangeCallback, final Surface surface) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
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

    private void startCommandClient(final String ip, final TouchSurfaceInterface touchSurfaceInterface) {
        new Thread() {
            public void run() {
                while (mIsRunning) {
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
