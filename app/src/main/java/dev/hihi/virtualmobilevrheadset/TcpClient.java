package dev.hihi.virtualmobilevrheadset;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    public void start(final String debugTag, final String ip, final int port, final boolean receiveMode) {
        mIsRunning = true;
        // Better way to handling threading?
        new Thread() {
            public void run() {
                Log.i(debugTag, "Starting tcp client");


                try (Socket clientSocket = new Socket(ip, port)) {
                    if (receiveMode) {
                        recvModeLoop(debugTag, clientSocket);
                    } else {
                        sendModeLoop(debugTag, clientSocket);
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

    private void sendModeLoop(final String debugTag, final Socket socket) {
        try (OutputStream os = socket.getOutputStream()) {
            while (mIsRunning) {
                // TODO: Better busy waiting?
                if (mPendingPacketQueue.size() == 0) {
                    if (socket.isClosed()) {
                        break;
                    }
                    continue;
                }
                if (DEBUG) {
                    Log.i(debugTag, "Ready to send, pending size: " + mPendingPacketQueue.size());
                }
                Packet packet = mPendingPacketQueue.poll();

                // Header: Length of packet
                byte[] header = new byte[4];
                header[0] =  (byte) ((packet.size >> 24) & 0xff);
                header[1] =  (byte) ((packet.size >> 16) & 0xff);
                header[2] =  (byte) ((packet.size >> 8) & 0xff);
                header[3] =  (byte) ((packet.size >> 0) & 0xff);
                os.write(header);

                // Payload
                os.write(packet.bytes, 0, packet.size);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void recvModeLoop(final String debugTag, final Socket clientSocket) {
        try (InputStream is = new BufferedInputStream(clientSocket.getInputStream())) {
            Log.i(debugTag, "Connected tcp client");

            byte[] header = new byte[4];

            Log.i(debugTag, "isRunning: " + mIsRunning);
            while (mIsRunning) {
                int headerRemain = 4;
                int headerOffset = 0;
                while (headerRemain != 0) {
                    if (!mIsRunning) {
                        return;
                    }
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
                while (nextPacketSize != 0) {
                    if (!mIsRunning) {
                        return;
                    }
                    int size = is.read(buffer, nextPacketOffset, nextPacketSize);
                    if (size > 0) {
                        nextPacketOffset += size;
                        nextPacketSize = nextPacketSize - size;
                    } else if (size < 0 || clientSocket.isClosed()) {
                        return;
                    }
                }
                mPendingPacketQueue.add(new Packet(buffer, nextPacketOffset));
                if (DEBUG && (SystemClock.uptimeMillis() - mLastDebugMessageTime)
                        > DEBUG_MESSAGE_INTERVAL_MS) {
                    mLastDebugMessageTime = SystemClock.uptimeMillis();
                    Log.v(debugTag, "receive packet size:" + nextPacketOffset);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    @Override
    public void sendBuf(byte[] buf, int len) {
        // TODO: Better buf limit ?
        if (mPendingPacketQueue.size() >= 200) {
            Log.w("TcpServer", "Buffer full, mPendingPacketQueue size: " + mPendingPacketQueue.size());
            return;
        }
        // TODO: need extra copy?
        byte[] bytes = new byte[len];
        System.arraycopy(buf, 0, bytes, 0, len);
        mPendingPacketQueue.add(new Packet(bytes, len));
    }

}
