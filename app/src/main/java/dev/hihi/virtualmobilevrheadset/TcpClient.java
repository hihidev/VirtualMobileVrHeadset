package dev.hihi.virtualmobilevrheadset;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class TcpClient implements MirrorClientInterface {

    private static final boolean DEBUG = true;
    private static final int DEBUG_MESSAGE_INTERVAL_MS = 1000;
    private static long mLastDebugMessageTime = 0;

    private boolean mIsConnected = false;
    private boolean mIsRunning = false;

    private CountDownLatch mStoppingLock = new CountDownLatch(1);

    private Queue<Packet> mPendingPacketQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start(final String debugTag, final String ip, final int port) {
        mIsRunning = true;
        // Better way to handling threading?
        new Thread() {
            public void run() {
                Log.i(debugTag, "Starting tcp client");


                try (Socket clientSocket = new Socket(ip, port);
                     InputStream is = new BufferedInputStream(clientSocket.getInputStream())) {
                    Log.i(debugTag, "Connected tcp client");

                    byte[] header = new byte[4];

                    Log.i(debugTag, "isRunning: " + mIsRunning);
                    while (mIsRunning) {
                        int headerRemain = 4;
                        int headerOffset = 0;
                        while (headerRemain != 0) {
                            int size = is.read(header, headerOffset, headerRemain);
                            if (size > 0) {
                                headerOffset += size;
                                headerRemain = headerRemain - size;
                            } else if (size < 0 || clientSocket.isClosed()) {
                                return;
                            }
                        }
                        int nextPacketSize =
                                (((header[0] & 0xff) << 24) | ((header[1] & 0xff) << 16) |
                                        ((header[2] & 0xff) << 8) | (header[3] & 0xff));
                        int nextPacketOffset = 0;

                        byte[] buffer = new byte[nextPacketSize];
                        while(nextPacketSize != 0) {
                            int size = is.read(buffer, nextPacketOffset, nextPacketSize);
                            if (size > 0) {
                                nextPacketOffset += size;
                                nextPacketSize = nextPacketSize - size;
                            } else if (size < 0 || clientSocket.isClosed()) {
                                return;
                            }
                        }
                        mPendingPacketQueue.add(new Packet(buffer, nextPacketOffset));
                        if (DEBUG && (SystemClock.uptimeMillis() - mLastDebugMessageTime) > DEBUG_MESSAGE_INTERVAL_MS) {
                            mLastDebugMessageTime = SystemClock.uptimeMillis();
                            Log.v(debugTag, "receive packet size:" + nextPacketOffset);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Log.v(debugTag, "Stopping");
                    mIsRunning = false;
                    mStoppingLock.countDown();
                }
            }
        }.start();
    }

    @Override
    public void stop() {
        mIsRunning = false;
    }

    @Override
    public boolean isConnected() {
        return mIsConnected;
    }

    @Override
    public void waitUntilStopped() {
        try {
            synchronized (mStoppingLock) {
                mStoppingLock.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Packet getNextPacket() {
        return mPendingPacketQueue.poll();
    }

    @Override
    public int packetQueueSize() {
        return mPendingPacketQueue.size();
    }
}
