
package sn.uasz.group2.p2p;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/** Indexe les fichiers d'un dossier, MAJ via WatchService. */
public class SharedIndex implements AutoCloseable {
    private static final Logger log = Logger.getLogger(SharedIndex.class.getName());
    private final Path root;
    private final Map<String, FileMetadata> index = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = true;

    public SharedIndex(Path root) throws IOException {
        this.root = root;
        if (!Files.exists(root)) Files.createDirectories(root);
        this.watchService = FileSystems.getDefault().newWatchService();
        root.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        rescan();
        startWatcher();
    }

    public List<FileMetadata> list() {
        rw.readLock().lock();
        try { return new ArrayList<>(index.values()); }
        finally { rw.readLock().unlock(); }
    }

    public File resolve(String name) { return root.resolve(name).toFile(); }

    private void rescan() {
        rw.writeLock().lock();
        try {
            index.clear();
            File[] files = root.toFile().listFiles(File::isFile);
            if (files == null) return;
            for (File f : files) {
                try {
                    String hex = CryptoUtils.sha256Hex(f);
                    index.put(f.getName(), new FileMetadata(f.getName(), f.length(), hex, f.lastModified()));
                } catch (IOException e) {
                    log.warning("Checksum error for " + f.getName() + ": " + e.getMessage());
                }
            }
        } finally { rw.writeLock().unlock(); }
    }

    private void startWatcher() {
        watchThread = new Thread(() -> {
            while (running) {
                try {
                    WatchKey key = watchService.take();
                    boolean changed = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        changed = true;
                    }
                    if (changed) rescan();
                    key.reset();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    @Override public void close() throws IOException {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        watchService.close();
    }
}
