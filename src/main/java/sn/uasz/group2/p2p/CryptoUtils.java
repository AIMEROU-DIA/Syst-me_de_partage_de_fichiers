package sn.uasz.group2.p2p;

import java.io.*;
import java.security.MessageDigest;
import java.security.DigestInputStream;

public final class CryptoUtils {
    private CryptoUtils() {}

    /** SHA-256 en hex (compatible Java 11) */
    public static String sha256Hex(File f) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(in, md)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) { /* read all */ }
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            throw new IOException("SHA-256 error: " + e.getMessage(), e);
        }
    }

    /** SHA-256 brut (32 octets) */
    public static byte[] sha256Bytes(File f) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return md.digest();
        } catch (Exception e) {
            throw new IOException("SHA-256 error: " + e.getMessage(), e);
        }
    }

    /** Utilitaire hex (Java 11) */
    public static String bytesToHex(byte[] bytes) {
        final char[] HEX = "0123456789ABCDEF".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
