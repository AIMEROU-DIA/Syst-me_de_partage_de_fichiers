
package sn.uasz.group2.p2p;

import java.io.Serializable;

/** Métadonnées d'un fichier partagé. */
public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final long size;
    private final String sha256Hex;
    private final long lastModified;

    public FileMetadata(String name, long size, String sha256Hex, long lastModified) {
        this.name = name;
        this.size = size;
        this.sha256Hex = sha256Hex;
        this.lastModified = lastModified;
    }
    public String getName() { return name; }
    public long getSize() { return size; }
    public String getSha256Hex() { return sha256Hex; }
    public long getLastModified() { return lastModified; }

    @Override public String toString() {
        return String.format("%s (%d bytes)", name, size);
    }
}
