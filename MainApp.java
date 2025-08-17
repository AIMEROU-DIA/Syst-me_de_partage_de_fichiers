package sn.uasz.group2.p2p;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.LogManager;

import javax.swing.SwingUtilities;

public class MainApp {
    public static void main(String[] args) {
      // Logging console lisible
      System.setProperty("java.util.logging.SimpleFormatter.format",
              "%1$tF %1$tT %4$s %2$s - %5$s%6$s%n");
      try { LogManager.getLogManager().readConfiguration(); } catch (Exception ignore) {}

      // Valeurs par défaut
      int port = 5000;
      Path share = Paths.get(System.getProperty("user.home"), "Partage");
      Path downloads = Paths.get(System.getProperty("user.home"), "Téléchargements", "P2P");

      // Parsing très simple des arguments
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "--port":
            if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
            break;
          case "--share":
            if (i + 1 < args.length) share = Paths.get(args[++i]);
            break;
          case "--downloads":
            if (i + 1 < args.length) downloads = Paths.get(args[++i]);
            break;
        }
      }

      final int p = port;
      final String s = share.toString();
      final String d = downloads.toString();

      SwingUtilities.invokeLater(() -> {
        MainFrame frame = new MainFrame(p, s, d); 
        frame.setVisible(true);
      });
    }
}
