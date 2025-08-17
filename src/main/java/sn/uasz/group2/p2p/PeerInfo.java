
package sn.uasz.group2.p2p;

import java.net.InetAddress;
import java.util.Objects;

public class PeerInfo {
    public final InetAddress address;
    public final int port;
    public long lastSeen;

    public PeerInfo(InetAddress address, int port, long lastSeen) {
        this.address = address;
        this.port = port;
        this.lastSeen = lastSeen;
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof PeerInfo)) return false;
        PeerInfo p = (PeerInfo)o;
        return port == p.port && Objects.equals(address, p.address);
    }
    @Override public int hashCode() { return Objects.hash(address, port); }
    @Override public String toString() { return address.getHostAddress() + ":" + port; }
}
