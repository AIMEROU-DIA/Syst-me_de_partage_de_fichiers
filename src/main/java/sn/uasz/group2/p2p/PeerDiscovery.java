
package sn.uasz.group2.p2p;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Découverte des pairs via UDP Multicast. */
public class PeerDiscovery implements AutoCloseable {
    private static final Logger log = Logger.getLogger(PeerDiscovery.class.getName());
    public static final String GROUP = "230.0.0.1";
    public static final int PORT = 4446;

    private final int tcpPort;
    private final MulticastSocket socket;
    private final InetAddress group;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private Thread recvThread, sendThread;
    private volatile boolean running = true;

    public PeerDiscovery(int tcpPort) throws IOException {
        this.tcpPort = tcpPort;
        this.group = InetAddress.getByName(GROUP);
        this.socket = new MulticastSocket(PORT);
        this.socket.joinGroup(group);
        start();
    }

    public Collection<PeerInfo> getPeers() {
        // Nettoyage pairs non vus depuis 10s
        long now = System.currentTimeMillis();
        peers.values().removeIf(p -> now - p.lastSeen > 10000);
        return new ArrayList<>(peers.values());
    }

    private void start() {
        recvThread = new Thread(this::recvLoop, "discovery-recv");
        recvThread.setDaemon(true);
        recvThread.start();
        sendThread = new Thread(this::sendLoop, "discovery-send");
        sendThread.setDaemon(true);
        sendThread.start();
    }

    private void recvLoop() {
        byte[] buf = new byte[256];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                if (msg.startsWith("HELLO ")) {
                    String[] parts = msg.split(" ");
                    int port = Integer.parseInt(parts[2]);
                    PeerInfo p = new PeerInfo(packet.getAddress(), port, System.currentTimeMillis());
                    peers.put(p.toString(), p);
                }
            } catch (IOException e) {
                if (running) log.warning("Discovery recv error: " + e.getMessage());
            }
        }
    }

    private void sendLoop() {
        while (running) {
            try {
                String msg = "HELLO " + InetAddress.getLocalHost().getHostAddress() + " " + tcpPort;
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
                socket.send(packet);
                Thread.sleep(3000);
            } catch (Exception e) {
                if (running) log.warning("Discovery send error: " + e.getMessage());
            }
        }
    }

    @Override public void close() throws IOException {
        running = false;
        if (recvThread != null) recvThread.interrupt();
        if (sendThread != null) sendThread.interrupt();
        socket.leaveGroup(group);
        socket.close();
    }
}
