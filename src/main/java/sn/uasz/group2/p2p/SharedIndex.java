package sn.uasz.group2.p2p;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Indexe les fichiers d'un dossier, MAJ via WatchService.
 */
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
        root.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        );

        rescan();       // première indexation
        startWatcher(); // surveillance continue
    }

    /** Liste courante des métadonnées (copie) sous read-lock. */
    public List<FileMetadata> list() {
        rw.readLock().lock();
        try {
            return new ArrayList<>(index.values());
        } finally {
            rw.readLock().unlock();
        }
    }

    /** Résout un nom de fichier dans le dossier racine. */
    public File resolve(String name) {
        return root.resolve(name).toFile();
    }

    /**
     * Exécute un bloc sous le verrou de LECTURE (read-lock).
     * Utile pour protéger les lectures longues (ex: envoi de fichier).
     */
    public <T> T withReadLock(java.util.concurrent.Callable<T> action) throws Exception {
        rw.readLock().lock();
        try {
            return action.call();
        } finally {
            rw.readLock().unlock();
        }
    }

    /** Rescan complet du dossier et recalcule les checksums (write-lock). */
    private void rescan() {
        rw.writeLock().lock();
        try {
            index.clear();
            File[] files = root.toFile().listFiles(File::isFile);
            if (files == null) return;

            for (File f : files) {
                try {
                    String hex = CryptoUtils.sha256Hex(f);
                    index.put(
                        f.getName(),
                        new FileMetadata(f.getName(), f.length(), hex, f.lastModified())
                    );
                } catch (IOException e) {
                    log.warning("Checksum error for " + f.getName() + ": " + e.getMessage());
                }
            }
            log.info("Index reconstruit: " + index.size() + " fichier(s).");
        } finally {
            rw.writeLock().unlock();
        }
    }

    /** Démarre le watcher FS et déclenche rescan() en cas de changement. */
    private void startWatcher() {
        watchThread = new Thread(() -> {
            while (running) {
                try {
                    WatchKey key = watchService.take(); // bloquant
                    boolean changed = false;

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        // Overflow = événements perdus (on rescanera de toute façon)
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            log.info("WatchService OVERFLOW: certains événements ont pu être perdus.");
                            changed = true;
                            continue;
                        }

                        // Contexte attendu: nom du fichier (relatif à 'root')
                        Path fileName = (Path) event.context();
                        log.info("Changement détecté: " + kind.name() + " -> " + fileName);

                        // Ici on pourrait faire une MAJ incrémentale du 'index',
                        // mais pour rester simple et robuste : on déclenche un rescan complet.
                        changed = true;
                    }

                    if (changed) rescan();

                    // Important: réarmer la clé, sinon on ne reçoit plus les événements
                    boolean valid = key.reset();
                    if (!valid) {
                        log.warning("WatchKey invalide — arrêt de la surveillance sur: " + root);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // sortie propre du thread
                } catch (Exception e) {
                    // On log et on continue (sauf si stop demandé)
                    if (running) log.warning("Watcher error: " + e.getMessage());
                }
            }
        }, "watcher");

        watchThread.setDaemon(true);
        watchThread.start();
    }

    @Override
    public void close() throws IOException {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        watchService.close();
    }
}
