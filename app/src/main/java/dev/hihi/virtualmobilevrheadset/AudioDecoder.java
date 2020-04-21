package dev.hihi.virtualmobilevrheadset;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

public class AudioDecoder {

    private static final String TAG = "AudioDecoder";
    private static final boolean DEBUG = true;

    private static final int DEBUG_MESSAGE_INTERVAL_MS = 1000;
    private static long mLastDebugMessageTime = 0;

    private static final int SAMPLE_RATE = 44100; // Hz
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO;

    private boolean mIsRunning = false;
    private CountDownLatch mStoppingLock = new CountDownLatch(1);

    public void startDecoder(final MirrorClientInterface client) {
        mIsRunning = true;
        // Better threading?
        new Thread(new Runnable() {
            @Override
            public void run() {
                AudioTrack audioTrack = null;
                try {
                    int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                            CHANNEL_MASK,
                            ENCODING) * 2;
                    audioTrack = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(ENCODING)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(CHANNEL_MASK)
                                    .build())
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            // .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                            .setBufferSizeInBytes(bufferSize)
                            .build();


                    audioTrack.play();
                    Log.i(TAG, "Audio streaming started");

                    while (mIsRunning) {
                        Packet packet = client.getNextPacket();
                        // TODO: No busy waiting
                        if (packet == null) {
                            SystemClock.sleep(1);
                            continue;
                        }
                        audioTrack.write(packet.bytes, 0, packet.size, AudioTrack.WRITE_BLOCKING);
                        if (DEBUG && (SystemClock.uptimeMillis() - mLastDebugMessageTime) > DEBUG_MESSAGE_INTERVAL_MS) {
                            mLastDebugMessageTime = SystemClock.uptimeMillis();
                            Log.v(TAG, "Wrote size:" + packet.size);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mIsRunning = false;
                    if (audioTrack != null) {
                        try {
                            audioTrack.stop();
                        } catch (Exception e){}
                        try {
                            audioTrack.release();
                        } catch (Exception e){}
                    }
                    mStoppingLock.countDown();
                }
            }
        }).start();
    }

    public void stop() {
        mIsRunning = false;
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public void waitUntilStopped() {
        try {
            synchronized (mStoppingLock) {
                mStoppingLock.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
