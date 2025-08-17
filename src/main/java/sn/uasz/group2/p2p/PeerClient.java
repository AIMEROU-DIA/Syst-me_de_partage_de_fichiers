package sn.uasz.group2.p2p;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Client pour contacter un pair. */
public class PeerClient {
    private static final Logger log = Logger.getLogger(PeerClient.class.getName());

    public List<FileMetadata> list(InetAddress host, int port) throws IOException, ClassNotFoundException {
        try (Socket s = new Socket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
             InputStream in = s.getInputStream()) {
            out.write("LIST\n");
            out.flush();
            ObjectInputStream ois = new ObjectInputStream(in);
            Object obj = ois.readObject();
            @SuppressWarnings("unchecked")
            List<FileMetadata> list = (List<FileMetadata>) obj;
            return new ArrayList<>(list);
        }
    }

    public Path download(InetAddress host, int port, String filename, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        Path dest = destDir.resolve(filename);
        try (Socket s = new Socket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
             BufferedReader rdr = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

            out.write("GET " + filename + "\n");
            out.flush();
            String header = rdr.readLine();
            if (header == null || !header.equals("OK")) {
                throw new IOException("Serveur a répondu: " + header);
            }
            DataInputStream din = new DataInputStream(s.getInputStream());
            long length = din.readLong();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(dest))) {
                byte[] buf = new byte[8192];
                long remaining = length;
                while (remaining > 0) {
                    int n = din.read(buf, 0, (int)Math.min(buf.length, remaining));
                    if (n == -1) throw new EOFException("Flux terminé prématurément");
                    fileOut.write(buf, 0, n);
                    md.update(buf, 0, n);
                    remaining -= n;
                }
            } catch (Exception e) {
                try { Files.deleteIfExists(dest); } catch (IOException ignore) {}
                throw e;
            }
            // vérifier checksum
            byte[] expected = din.readNBytes(32);
            String got = CryptoUtils.bytesToHex(md.digest());
            String exp = CryptoUtils.bytesToHex(expected);
            if (!got.equals(exp)) {
                try { Files.deleteIfExists(dest); } catch (IOException ignore) {}
                throw new IOException("Checksum invalide: attendu " + exp + ", reçu " + got);
            }
            log.info("Téléchargé: " + dest + " (" + length + " octets) OK");
            return dest;
        } catch (Exception e) {
            throw new IOException("Download failed: " + e.getMessage(), e);
        }
    }
}
