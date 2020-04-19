package dev.hihi.virtualmobilevrheadset;

import java.net.InetSocketAddress;

public interface MirrorClientInterface {
    void start(final String ip, final int port, final Runnable connectedCallback,
            final Runnable stoppedCallback, final boolean receiveMode);
    void stop();
    boolean isConnected();
    void waitUntilStopped();
    Packet getNextPacket();
    int packetQueueSize();
    void sendBuf(byte[] buf, int len);
}
