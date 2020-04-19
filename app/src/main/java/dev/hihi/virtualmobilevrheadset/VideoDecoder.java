package dev.hihi.virtualmobilevrheadset;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

public class VideoDecoder {

    private static final String TAG = "VideoDecoder";
    private static final String MIME_TYPE = "video/avc";
    private static boolean DEBUG = true;

    private int mWidth = 0;
    private int mHeight = 0;
    private boolean mIsRotated = false;

    private boolean mIsStopped = false;
    private CountDownLatch mCountDownLatch = new CountDownLatch(2);

    public interface OnSizeChangeCallback {
        void onChange(int width, int height, boolean isRotated);
    }

    public void startDecoder(final OnSizeChangeCallback onSizeChangeCallback,
            final Surface surface, final boolean isLandscapeScreen, final MirrorClientInterface client) {
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
                        decoder.configure(createFormat(packet, isLandscapeScreen), surface, null, 0);
                        onSizeChangeCallback.onChange(mWidth, mHeight, mIsRotated);
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

    private MediaFormat createFormat(Packet configPacket, boolean isLandscapeScreen) {
        int width = (configPacket.bytes[0] & 0xff) * 256  + (configPacket.bytes[1] & 0xff);
        int height = (configPacket.bytes[2] & 0xff) * 256 + (configPacket.bytes[3] & 0xff);

        Log.i(TAG, "createFormat with width: " + width + ", height: " + height + ", mime_type: " + MIME_TYPE);

        MediaFormat result = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        result.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        // result.setInteger("allow-frame-drop", 0);

        boolean isLandscapeVideo = width > height;
        if (isLandscapeScreen != isLandscapeVideo) {
            result.setInteger(MediaFormat.KEY_ROTATION, 90);
            mWidth = height;
            mHeight = width;
            mIsRotated = true;
        } else {
            mWidth = width;
            mHeight = height;
            mIsRotated = false;
        }

        return result;
    }

    public void stop() {
        mIsStopped = true;
    }
}
