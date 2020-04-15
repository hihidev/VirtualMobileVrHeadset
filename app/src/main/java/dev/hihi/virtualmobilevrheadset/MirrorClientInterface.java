package dev.hihi.virtualmobilevrheadset;

import java.net.InetSocketAddress;

public interface MirrorClientInterface {
    void start(final String ip, final int port);
    void stop();
    boolean isConnected();
    void waitUntilStopped();
    Packet getNextPacket();
    int pendingPacketSize();
}
