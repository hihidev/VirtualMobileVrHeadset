package dev.hihi.virtualmobilevrheadset;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class TcpClient implements MirrorClientInterface {

    private static final String TAG = "TcpClient";
    private static final boolean DEBUG = true;

    private boolean isConnected = false;
    private boolean isRunning = false;

    private CountDownLatch mStoppingLock = new CountDownLatch(1);

    private Queue<Packet> mPendingPacketQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start(final String ip, final int port) {
        isRunning = true;
        // Better way to handling threading?
        new Thread() {
            public void run() {
                Log.i(TAG, "Starting tcp client");


                try (Socket clientSocket = new Socket(ip, port);
                     InputStream is = new BufferedInputStream(clientSocket.getInputStream())) {
                    Log.i(TAG, "Connected tcp client");

                    byte[] header = new byte[4];

                    Log.i(TAG, "isRunning: " + isRunning);
                    while (isRunning) {
                        int headerRemain = 4;
                        int headerOffset = 0;
                        while (headerRemain != 0) {
                            Log.i(TAG, "wait read: " + isRunning);
                            int size = is.read(header, headerOffset, headerRemain);
                            Log.i(TAG, "size: " + size);
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
                        if (DEBUG) {
                            Log.v(TAG, "nextPacketSize: " + nextPacketSize);
                        }

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
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Log.v(TAG, "Stopping");
                    isRunning = false;
                    mStoppingLock.countDown();
                }
            }
        }.start();
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping server");
        isRunning = false;
    }

    @Override
    public boolean isConnected() {
        return isConnected;
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
    public int pendingPacketSize() {
        return mPendingPacketQueue.size();
    }
}
