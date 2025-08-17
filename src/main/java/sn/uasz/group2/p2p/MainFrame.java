package sn.uasz.group2.p2p;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;

public class MainFrame extends JFrame {

    // Couleurs modernes
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color SECONDARY_COLOR = new Color(52, 73, 94);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);
    private static final Color DANGER_COLOR = new Color(231, 76, 60);
    private static final Color WARNING_COLOR = new Color(243, 156, 18);
    private static final Color LIGHT_GRAY = new Color(236, 240, 241);
    private static final Color DARK_GRAY = new Color(149, 165, 166);
    private static final Color WHITE = Color.WHITE;
    private static final Color ACCENT_COLOR = new Color(155, 89, 182);

    // Champs saisis par l'utilisateur
    private final JTextField portField;
    private final JTextField shareField;
    private final JTextField dlField;
    private Path defaultDownloadsDir;

    // UI listes avec modèles personnalisés
    private final DefaultListModel<String> peersModel = new DefaultListModel<>();
    private final JList<String> peersList = new JList<>(peersModel);
    private final DefaultListModel<FileMetadata> filesModel = new DefaultListModel<>();
    private final JList<FileMetadata> filesList = new JList<>(filesModel);
    private final JTextArea logArea = new JTextArea(8, 80);

    // Téléchargés par pair
    private final Map<String, DefaultListModel<String>> downloadedByPeer = new HashMap<>();
    private final JList<String> downloadedList = new JList<>(new DefaultListModel<>());

    // Boutons stylés
    private JButton listBtn, dlBtn, startBtn, stopBtn, browseDefaultBtn, browseShareBtn;
    private JLabel statusLabel;

    // Services
    private SharedIndex index;
    private PeerServer server;
    private PeerDiscovery discovery;
    private final javax.swing.Timer refreshTimer;
    private final PeerClient client = new PeerClient();

    // Cache adresses locales
    private Set<String> localAddrsCache;

    public MainFrame(int port, String sharePath, String downloadPath) {
        super("Système de Partage P2P - UASZ Groupe 2");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Taille par défaut plus compacte + taille minimale
        setPreferredSize(new Dimension(950, 600)); // taille initiale au lancement
        setMinimumSize(new Dimension(800, 500));   // empêche d'être trop petit
        setLocationRelativeTo(null);               // centre la fenêtre

        // Configuration du Look & Feel moderne (Java pur)
        try {
            // Utiliser Metal Look & Feel pour éviter les problèmes GTK
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            // Ou utiliser Nimbus qui est plus moderne
            // UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            // Utiliser le look par défaut si échec
        }

        // Initialisation des champs
        this.portField = createStyledTextField(String.valueOf(port));
        this.shareField = createStyledTextField(sharePath);
        this.defaultDownloadsDir = Paths.get(downloadPath);
        this.dlField = createStyledTextField(defaultDownloadsDir.toString());

        // Configuration des listes
        setupLists();

        // Construction de l'interface
        buildUI();

        // Ajuste la taille en fonction du contenu tout en respectant preferred/minimum
        pack();

        // Timer de rafraîchissement
        refreshTimer = new javax.swing.Timer(2000, e -> refreshPeers());
    }

    public MainFrame() {
        this(5000,
             System.getProperty("user.home") + "/Partage",
             System.getProperty("user.home") + "/Téléchargements/P2P");
    }

    private JTextField createStyledTextField(String text) {
        JTextField field = new JTextField(text);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        field.setBorder(new CompoundBorder(
            new LineBorder(DARK_GRAY, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        field.setBackground(WHITE);
        field.setPreferredSize(new Dimension(200, 35));
        return field;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(bgColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bgColor.brighter());
                } else {
                    g2.setColor(bgColor);
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();

                super.paintComponent(g);
            }
        };

        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setForeground(WHITE);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(120, 35));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return button;
    }

    private void setupLists() {
        // Configuration de la liste des pairs
        peersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peersList.setFont(new Font("Consolas", Font.PLAIN, 12));
        peersList.setBackground(WHITE);
        peersList.setBorder(new EmptyBorder(8, 8, 8, 8));
        peersList.setSelectionBackground(PRIMARY_COLOR);
        peersList.setSelectionForeground(WHITE);

        peersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) onListRemote(null);
            }
        });

        peersList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String sel = peersList.getSelectedValue();
            if (sel != null) downloadedList.setModel(getDownloadedModelFor(sel));
        });

        // Configuration de la liste des fichiers
        filesList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        filesList.setBackground(WHITE);
        filesList.setBorder(new EmptyBorder(8, 8, 8, 8));
        filesList.setSelectionBackground(ACCENT_COLOR);
        filesList.setSelectionForeground(WHITE);

        // Configuration de la liste des téléchargements
        downloadedList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        downloadedList.setBackground(LIGHT_GRAY);
        downloadedList.setBorder(new EmptyBorder(8, 8, 8, 8));
        downloadedList.setSelectionBackground(SUCCESS_COLOR);
        downloadedList.setSelectionForeground(WHITE);

        // Configuration de la zone de logs
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setBackground(new Color(248, 249, 250));
        logArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
    }

    private void buildUI() {
        setBackground(LIGHT_GRAY);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(LIGHT_GRAY);

        // Header avec titre et statut
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Panel de contrôles
        JPanel controlsPanel = createControlsPanel();
        mainPanel.add(controlsPanel, BorderLayout.CENTER);

        // Panel des logs
        JPanel logsPanel = createLogsPanel();
        mainPanel.add(logsPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SECONDARY_COLOR);
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel titleLabel = new JLabel("Système de Partage P2P");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(WHITE);

        statusLabel = new JLabel("● Arrêté");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(DANGER_COLOR);

        JLabel subtitleLabel = new JLabel("UASZ - Groupe 2");
        subtitleLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        subtitleLabel.setForeground(DARK_GRAY);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel, BorderLayout.NORTH);
        titlePanel.add(subtitleLabel, BorderLayout.SOUTH);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);

        return header;
    }

    private JPanel createControlsPanel() {
        JPanel controls = new JPanel(new BorderLayout(0, 16));
        controls.setBackground(LIGHT_GRAY);
        controls.setBorder(new EmptyBorder(20, 20, 10, 20));

        // Panel de configuration
        JPanel configPanel = createConfigPanel();
        controls.add(configPanel, BorderLayout.NORTH);

        // Panel principal avec listes
        JPanel mainContentPanel = createMainContentPanel();
        controls.add(mainContentPanel, BorderLayout.CENTER);

        return controls;
    }

    private JPanel createConfigPanel() {
        JPanel config = new JPanel(new GridLayout(2, 1, 0, 12));
        config.setBackground(WHITE);
        config.setBorder(new CompoundBorder(
            new LineBorder(DARK_GRAY.brighter(), 1),
            new EmptyBorder(20, 20, 20, 20)
        ));

        // Première ligne : Port et dossier partagé
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row1.setOpaque(false);

        row1.add(createLabel("Port TCP:"));
        row1.add(portField);
        row1.add(createLabel("Dossier partagé:"));
        row1.add(shareField);

        browseShareBtn = createStyledButton("Parcourir", WARNING_COLOR);
        browseShareBtn.addActionListener(this::onBrowseShare);
        row1.add(browseShareBtn);

        startBtn = createStyledButton("● Démarrer", SUCCESS_COLOR);
        startBtn.addActionListener(this::onStart);
        row1.add(startBtn);

        // Deuxième ligne : Téléchargements et actions
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row2.setOpaque(false);

        row2.add(createLabel("Téléchargements:"));
        row2.add(dlField);

        browseDefaultBtn = createStyledButton("Parcourir", WARNING_COLOR);
        browseDefaultBtn.addActionListener(this::onBrowseDefaultDownloads);
        row2.add(browseDefaultBtn);

        listBtn = createStyledButton("📋 Lister", PRIMARY_COLOR);
        listBtn.addActionListener(this::onListRemote);
        row2.add(listBtn);

        dlBtn = createStyledButton("⬇ Télécharger", ACCENT_COLOR);
        dlBtn.addActionListener(this::onDownload);
        row2.add(dlBtn);

        stopBtn = createStyledButton("⏹ Arrêter", DANGER_COLOR);
        stopBtn.addActionListener(e -> onStop());
        row2.add(stopBtn);

        config.add(row1);
        config.add(row2);

        return config;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(SECONDARY_COLOR);
        return label;
    }

    private JPanel createMainContentPanel() {
        JPanel main = new JPanel(new BorderLayout(16, 0));
        main.setOpaque(false);

        // Panel des pairs (gauche)
        JPanel peersPanel = createStyledPanel("🌐 Pairs Détectés", new JScrollPane(peersList));
        peersPanel.setPreferredSize(new Dimension(300, 400));

        // Panel de droite (fichiers + téléchargements)
        JPanel rightPanel = new JPanel(new BorderLayout(0, 16));
        rightPanel.setOpaque(false);

        JPanel filesPanel = createStyledPanel("📁 Fichiers Disponibles", new JScrollPane(filesList));
        JPanel downloadsPanel = createStyledPanel("✅ Téléchargements Terminés", new JScrollPane(downloadedList));

        rightPanel.add(filesPanel, BorderLayout.CENTER);
        rightPanel.add(downloadsPanel, BorderLayout.SOUTH);

        main.add(peersPanel, BorderLayout.WEST);
        main.add(rightPanel, BorderLayout.CENTER);

        return main;
    }

    private JPanel createStyledPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(WHITE);
        panel.setBorder(new CompoundBorder(
            new LineBorder(DARK_GRAY.brighter(), 1),
            new EmptyBorder(0, 0, 0, 0)
        ));

        // Header du panel
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_COLOR.brighter());
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setForeground(WHITE);

        header.add(titleLabel, BorderLayout.WEST);

        panel.add(header, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel logs = new JPanel(new BorderLayout());
        logs.setBackground(LIGHT_GRAY);
        logs.setBorder(new EmptyBorder(10, 20, 20, 20));

        JPanel logsContent = createStyledPanel("📋 Journal d'Activité", new JScrollPane(logArea));
        logsContent.setPreferredSize(new Dimension(-1, 180));

        logs.add(logsContent, BorderLayout.CENTER);

        return logs;
    }

    // Méthodes d'événements (logique inchangée)
    private void onBrowseShare(ActionEvent e) {
        JFileChooser fc = new JFileChooser(shareField.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            shareField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onBrowseDefaultDownloads(ActionEvent e) {
        JFileChooser fc = new JFileChooser(dlField.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            defaultDownloadsDir = fc.getSelectedFile().toPath();
            dlField.setText(defaultDownloadsDir.toString());
            logln("✓ Dossier de téléchargements configuré : " + defaultDownloadsDir);
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

            statusLabel.setText("● En ligne");
            statusLabel.setForeground(SUCCESS_COLOR);

            logln("🚀 Nœud démarré avec succès sur le port " + port);
            logln("📂 Dossier partagé : " + share);
        } catch (Exception ex) {
            statusLabel.setText("● Erreur");
            statusLabel.setForeground(DANGER_COLOR);
            logln("❌ Erreur démarrage: " + ex.getMessage());
        }
    }

    private void onStop() {
        try {
            if (discovery != null) discovery.close();
            if (server != null) server.close();
            if (index != null) index.close();
            refreshTimer.stop();

            statusLabel.setText("● Arrêté");
            statusLabel.setForeground(DANGER_COLOR);

            logln("⏹ Nœud arrêté proprement.");
        } catch (Exception ex) {
            logln("❌ Erreur lors de l'arrêt: " + ex.getMessage());
        }
    }

    private void refreshPeers() {
        if (discovery == null) return;

        String selectedBefore = peersList.getSelectedValue();
        Set<String> current = new LinkedHashSet<>();
        for (int i = 0; i < peersModel.size(); i++) current.add(peersModel.get(i));

        List<String> newPeers = new ArrayList<>();
        for (PeerInfo p : discovery.getPeers()) {
            if (!isSelf(p)) {
                newPeers.add(p.toString());
            }
        }

        Set<String> next = new LinkedHashSet<>(newPeers);
        if (!next.equals(current)) {
            peersModel.clear();
            for (String s : next) peersModel.addElement(s);
        }

        if (selectedBefore != null) {
            int found = -1;
            for (int i = 0; i < peersModel.size(); i++) {
                if (selectedBefore.equals(peersModel.get(i))) { found = i; break; }
            }
            if (found >= 0) peersList.setSelectedIndex(found);
        }

        String sel = peersList.getSelectedValue();
        if (sel != null) downloadedList.setModel(getDownloadedModelFor(sel));
    }

    private void onListRemote(ActionEvent e) {
        final String sel = peersList.getSelectedValue();
        if (sel == null) {
            logln("⚠️ Veuillez sélectionner un pair.");
            return;
        }

        setButtonsEnabled(false, true);
        new Thread(() -> {
            try {
                String[] hp = sel.split(":");
                InetAddress host = InetAddress.getByName(hp[0]);
                int port = Integer.parseInt(hp[1]);
                List<FileMetadata> list = client.list(host, port);

                SwingUtilities.invokeLater(() -> {
                    filesModel.clear();
                    for (FileMetadata fm : list) filesModel.addElement(fm);
                    logln("📋 Liste reçue de " + sel + " : " + list.size() + " fichier(s)");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> logln("❌ Erreur LIST: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(true, null));
            }
        }, "list-" + sel).start();
    }

    private void onDownload(ActionEvent e) {
        final String selPeer = peersList.getSelectedValue();
        final FileMetadata meta = filesList.getSelectedValue();
        if (selPeer == null || meta == null) {
            logln("⚠️ Sélectionnez un pair et un fichier.");
            return;
        }

        final Path destDir = defaultDownloadsDir;
        setButtonsEnabled(null, false);

        new Thread(() -> {
            try {
                String[] hp = selPeer.split(":");
                InetAddress host = InetAddress.getByName(hp[0]);
                int port = Integer.parseInt(hp[1]);

                Path path = client.download(host, port, meta.getName(), destDir);

                SwingUtilities.invokeLater(() -> {
                    logln("✅ Téléchargement terminé : " + path.getFileName() + " depuis " + selPeer);
                    DefaultListModel<String> model = getDownloadedModelFor(selPeer);
                    model.addElement(path.getFileName().toString());
                    if (selPeer.equals(peersList.getSelectedValue())) {
                        downloadedList.setModel(model);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> logln("❌ Erreur téléchargement: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(null, true));
            }
        }, "download-" + meta.getName()).start();
    }

    // Méthodes utilitaires (logique inchangée)
    private boolean isSelf(PeerInfo p) {
        try {
            String repr = p.toString().trim();
            int sep = repr.lastIndexOf(':');
            if (sep < 0) return false;
            String host = repr.substring(0, sep);
            int port = Integer.parseInt(repr.substring(sep + 1));

            int myPort = Integer.parseInt(portField.getText().trim());
            if (port != myPort) return false;

            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress()) return true;

            return getLocalAddrs().contains(addr.getHostAddress());
        } catch (Exception e) {
            return false;
        }
    }

    private Set<String> getLocalAddrs() {
        if (localAddrsCache != null) return localAddrsCache;
        Set<String> set = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    set.add(a.getHostAddress());
                }
            }
        } catch (SocketException ignored) {}
        set.add("127.0.0.1");
        set.add("::1");
        localAddrsCache = set;
        return localAddrsCache;
    }

    private DefaultListModel<String> getDownloadedModelFor(String peerKey) {
        DefaultListModel<String> model = downloadedByPeer.get(peerKey);
        if (model == null) {
            model = new DefaultListModel<>();
            downloadedByPeer.put(peerKey, model);
        }
        return model;
    }

    private void setButtonsEnabled(Boolean listEnabled, Boolean downloadEnabled) {
        if (listEnabled != null) listBtn.setEnabled(listEnabled);
        if (downloadEnabled != null) dlBtn.setEnabled(downloadEnabled);
    }

    private void logln(String s) {
        logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
