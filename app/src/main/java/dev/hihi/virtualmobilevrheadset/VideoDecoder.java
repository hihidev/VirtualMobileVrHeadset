package dev.hihi.virtualmobilevrheadset;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

public class VideoDecoder {

    private static final String TAG = "VideoDecoder";
    private static final String MIME_TYPE = "video/avc";
    private static boolean DEBUG = true;

    private int mWidth = 0;
    private int mHeight = 0;
    private boolean mIsStopped = false;
    private CountDownLatch mCountDownLatch = new CountDownLatch(2);


    public void startDecoder(final WindowManager windowManager, final MyTextureView view,
            final Surface surface, final MirrorClientInterface client) {
        mIsStopped = false;
        MediaCodec decoderReal;
        try {
            decoderReal = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
            decoderReal = null;
        }
        final MediaCodec decoder = decoderReal;

        final Thread outputBufThread = new Thread() {
            public void run() {
                while (!mIsStopped) {
                    try {
                        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                        int outIndex = decoder.dequeueOutputBuffer(info, 100_000);
                        if (outIndex >= 0) {
                            decoder.releaseOutputBuffer(outIndex, true);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                mCountDownLatch.countDown();
            }
        };

        new Thread(new Runnable() {
            boolean firstFrame = true;
            @Override
            public void run() {
                try {
                    Packet packet = null;
                    // TODO: No busy waiting
                    while ((packet = client.getNextPacket()) == null && !mIsStopped) {
                        SystemClock.sleep(1);
                    }
                    if (mIsStopped) {
                        mCountDownLatch.countDown();
                        return;
                    }

                    try {
                        decoder.configure(createFormat(packet), surface, null, 0);
                        updateSurfaceSize(windowManager, view);
                        decoder.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                        mCountDownLatch.countDown();
                        return;
                    }
                    outputBufThread.start();

                    Log.v(TAG, "Video streaming started");

                    int inIndex;
                    while (!mIsStopped) {
                        packet = null;
                        // TODO: No busy waiting
                        while ((packet = client.getNextPacket()) == null && !mIsStopped) {
                            SystemClock.sleep(1);
                        }
                        if (mIsStopped) {
                            break;
                        }
                        if (DEBUG) {
                            Log.v(TAG, "packets remain: " + client.packetQueueSize());
                        }

                        while ((inIndex = decoder.dequeueInputBuffer(100_000)) == -1 && !mIsStopped) {
                            SystemClock.sleep(1);
                        }
                        if (mIsStopped) {
                            break;
                        }

                        if (firstFrame && DEBUG) {
                            Log.v(TAG, "Processing first frame");
                        }

                        ByteBuffer codecBuffer = decoder.getInputBuffer(inIndex);
                        codecBuffer.clear();
                        codecBuffer.put(packet.bytes, 0, packet.size);
                        decoder.queueInputBuffer(inIndex, 0, packet.size, 0, firstFrame ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0);
                        firstFrame = false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    decoder.stop();
                    mCountDownLatch.countDown();
                    mIsStopped = true;
                }
            }
        }).start();
    }

    public void waitUntilStopped() {
        try {
            synchronized (mCountDownLatch) {
                mCountDownLatch.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private MediaFormat createFormat(Packet configPacket) {
        mWidth = (configPacket.bytes[0] & 0xff) * 256  + (configPacket.bytes[1] & 0xff);
        mHeight = (configPacket.bytes[2] & 0xff) * 256 + (configPacket.bytes[3] & 0xff);

        Log.i(TAG, "createFormat with width: " + mWidth + ", height: " + mHeight + ", mime_type: " + MIME_TYPE);

        MediaFormat result = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        result.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        // result.setInteger("allow-frame-drop", 0);
        return result;
    }

    private void updateSurfaceSize(WindowManager windowManager, final MyTextureView view) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        float screenProportion = (float) displayMetrics.widthPixels / (float) displayMetrics.heightPixels;

        float videoProportion = (float) mWidth / (float) mHeight;

        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (videoProportion > screenProportion) {
            lp.width = displayMetrics.widthPixels;
            lp.height = (int) ((float) displayMetrics.widthPixels / videoProportion);
        } else {
            lp.width = (int) (videoProportion * (float) displayMetrics.heightPixels);
            lp.height = displayMetrics.heightPixels;
        }

        view.post(new Runnable() {
            @Override
            public void run() {
                view.setLayoutParams(lp);
            }
        });
        view.setVideoSourceSize(mWidth, mHeight);
    }

    public void stop() {
        mIsStopped = true;
    }
}
