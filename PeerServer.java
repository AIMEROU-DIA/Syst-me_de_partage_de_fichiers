
package sn.uasz.group2.p2p;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Serveur TCP d'un pair : gère LIST et GET. */
public class PeerServer implements AutoCloseable {
    private static final Logger log = Logger.getLogger(PeerServer.class.getName());
    private final int port;
    private final SharedIndex index;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = true;
    private ServerSocket server;

    public PeerServer(int port, SharedIndex index) {
        this.port = port;
        this.index = index;
    }

    public void start() throws IOException {
        server = new ServerSocket(port);
        pool.submit(() -> {
            log.info("Serveur TCP démarré sur le port " + port);
            while (running) {
                try {
                    Socket s = server.accept();
                    pool.submit(() -> handle(s));
                } catch (IOException e) {
                    if (running) log.warning("Accept error: " + e.getMessage());
                }
            }
        });
    }

    private void handle(Socket s) {
        try (s;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             OutputStream rawOut = s.getOutputStream()) {

            String line = in.readLine();
            if (line == null) return;
            if (line.equals("LIST")) {
                List<FileMetadata> list = index.list();
                ObjectOutputStream oos = new ObjectOutputStream(rawOut);
                oos.writeObject(list);
                oos.flush();
            } else if (line.startsWith("GET ")) {
                String name = line.substring(4).trim();
                File f = index.resolve(name);
                if (!f.exists() || !f.isFile()) {
                    rawOut.write("ERR NotFound\n".getBytes());
                    rawOut.flush();
                    return;
                }
                // OK header
                rawOut.write("OK\n".getBytes());
                rawOut.flush();
                DataOutputStream out = new DataOutputStream(rawOut);
                out.writeLong(f.length());
                try (InputStream fileIn = new BufferedInputStream(new FileInputStream(f))) {
                    fileIn.transferTo(out);
                }
                out.write(CryptoUtils.sha256Bytes(f)); // 32 bytes
                out.flush();
            } else {
                rawOut.write("ERR BadCommand\n".getBytes());
                rawOut.flush();
            }
        } catch (IOException e) {
            log.warning("Client handler error: " + e.getMessage());
        }
    }

    @Override public void close() throws IOException {
        running = false;
        if (server != null) server.close();
        pool.shutdownNow();
    }
}
