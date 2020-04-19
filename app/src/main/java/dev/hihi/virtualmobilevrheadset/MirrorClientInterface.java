package dev.hihi.virtualmobilevrheadset;

import java.net.InetSocketAddress;

public interface MirrorClientInterface {
    void start(String ip, int port, Runnable stoppedCallback, boolean receiveMode);
    void stop();
    boolean isConnected();
    void waitUntilStopped();
    Packet getNextPacket();
    int packetQueueSize();
    void sendBuf(byte[] buf, int len);
}
