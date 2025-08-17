package sn.uasz.group2.p2p;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class MainFrame extends JFrame {
    private static final Logger log = Logger.getLogger(MainFrame.class.getName());

    // NE PAS initialiser ici avec des valeurs par défaut
    private final JTextField portField;
    private final JTextField shareField;
    private final JTextField dlField;

    private final DefaultListModel<String> peersModel = new DefaultListModel<>();
    private final JList<String> peersList = new JList<>(peersModel);
    private final DefaultListModel<FileMetadata> filesModel = new DefaultListModel<>();
    private final JList<FileMetadata> filesList = new JList<>(filesModel);
    private final JTextArea logArea = new JTextArea(8, 80);

    private SharedIndex index;
    private PeerServer server;
    private PeerDiscovery discovery;
    private final javax.swing.Timer refreshTimer; // Timer Swing explicite
    private final PeerClient client = new PeerClient();

    /** Constructeur "avec paramètres" : utilisé par MainApp */
    public MainFrame(int port, String sharePath, String downloadPath) {
        super("Systeme de partage de fichiers - UASZ / Groupe 2");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));

        // Champs initialisés avec les valeurs reçues
        this.portField = new JTextField(String.valueOf(port));
        this.shareField = new JTextField(sharePath);
        this.dlField = new JTextField(downloadPath);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        // Top controls
        JPanel top = new JPanel(new GridLayout(2, 1, 8, 8));
        JPanel row1 = new JPanel(new GridLayout(1, 6, 6, 6));
        row1.add(new JLabel("Port TCP:"));
        row1.add(portField);
        row1.add(new JLabel("Dossier partagé:"));
        row1.add(shareField);
        JButton browse = new JButton("Parcourir...");
        browse.addActionListener(this::onBrowse);
        row1.add(browse);
        JButton start = new JButton("Démarrer");
        start.addActionListener(this::onStart);
        row1.add(start);

        JPanel row2 = new JPanel(new GridLayout(1, 5, 6, 6));
        row2.add(new JLabel("Dossier téléchargements:"));
        row2.add(dlField);
        JButton listBtn = new JButton("Lister fichiers");
        listBtn.addActionListener(this::onListRemote);
        JButton dlBtn = new JButton("Télécharger");
        dlBtn.addActionListener(this::onDownload);
        JButton stopBtn = new JButton("Arrêter");
        stopBtn.addActionListener(e -> onStop());
        row2.add(listBtn);
        row2.add(dlBtn);
        row2.add(stopBtn);

        top.add(row1);
        top.add(row2);
        root.add(top, BorderLayout.NORTH);

        // Center split
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.3);
        split.setLeftComponent(wrap(new JScrollPane(peersList), "Pairs détectés"));
        split.setRightComponent(wrap(new JScrollPane(filesList), "Fichiers du pair sélectionné"));
        root.add(split, BorderLayout.CENTER);

        logArea.setEditable(false);
        root.add(wrap(new JScrollPane(logArea), "Logs"), BorderLayout.SOUTH);

        // Rafraîchissement périodique de la liste des pairs
        refreshTimer = new javax.swing.Timer(2000, e -> refreshPeers());
    }

    /** Constructeur de secours (valeurs par défaut) */
    public MainFrame() {
        this(5000,
             System.getProperty("user.home") + "/Partage",
             System.getProperty("user.home") + "/Téléchargements/P2P");
    }

    private JPanel wrap(JComponent c, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void onBrowse(ActionEvent e) {
        JFileChooser fc = new JFileChooser(shareField.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            shareField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onStart(ActionEvent e) {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            Path share = Paths.get(shareField.getText().trim());
            index = new SharedIndex(share);
            server = new PeerServer(port, index);
            server.start();
            discovery = new PeerDiscovery(port);
            refreshTimer.start();
            logln("Noeud démarré sur le port " + port + ", partage: " + share);
        } catch (Exception ex) {
            logln("Erreur démarrage: " + ex.getMessage());
        }
    }

    private void onStop() {
        try {
            if (discovery != null) discovery.close();
            if (server != null) server.close();
            if (index != null) index.close();
            refreshTimer.stop();
            logln("Noeud arrêté.");
        } catch (Exception ex) {
            logln("Erreur arrêt: " + ex.getMessage());
        }
    }

    private void refreshPeers() {
        if (discovery == null) return;
        peersModel.clear();
        for (PeerInfo p : discovery.getPeers()) {
            peersModel.addElement(p.toString());
        }
    }

    private void onListRemote(ActionEvent e) {
        String sel = peersList.getSelectedValue();
        if (sel == null) { logln("Sélectionnez un pair."); return; }
        try {
            String[] hp = sel.split(":");
            InetAddress host = InetAddress.getByName(hp[0]);
            int port = Integer.parseInt(hp[1]);
            List<FileMetadata> list = client.list(host, port);
            filesModel.clear();
            list.forEach(filesModel::addElement);
            logln("Liste reçue: " + list.size() + " fichier(s).");
        } catch (Exception ex) {
            logln("Erreur LIST: " + ex.getMessage());
        }
    }

    private void onDownload(ActionEvent e) {
        String selPeer = peersList.getSelectedValue();
        FileMetadata meta = filesList.getSelectedValue();
        if (selPeer == null || meta == null) { logln("Sélectionnez un pair et un fichier."); return; }
        try {
            String[] hp = selPeer.split(":");
            InetAddress host = InetAddress.getByName(hp[0]);
            int port = Integer.parseInt(hp[1]);
            Path destDir = Paths.get(dlField.getText().trim());
            java.nio.file.Path path = client.download(host, port, meta.getName(), destDir);
            logln("Téléchargé: " + path.getFileName());
        } catch (Exception ex) {
            logln("Erreur téléchargement: " + ex.getMessage());
        }
    }

    private void logln(String s) { logArea.append(s + "\n"); }
}
