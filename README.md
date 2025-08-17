# P2P File Share (Java 11 + Swing)

Un mini système **peer-to-peer** de partage de fichiers pour le module de **Programmation Réseau**.

## Fonctionnalités
- Découverte automatique des pairs en **UDP Multicast** (230.0.0.1:4446)
- Serveur TCP par pair (port configurable) : commandes `LIST` et `GET <nom>`
- Sérialisation des **métadonnées** (`FileMetadata`) via `ObjectOutputStream` (nom, taille, sha256, lastModified)
- Téléchargements/Uploads **concurrents** (thread par connexion, `ExecutorService`)
- Synchronisation d’accès au dossier partagé via `ReentrantReadWriteLock`
- Interface **Swing** (choix du port/dossier, liste des pairs, liste de fichiers, téléchargement, logs)
- Vérification d’intégrité **SHA‑256**

## Prérequis
- Java 11+
- Maven 3.8+

## Build
```bash
mvn -q -f p2p-fileshare-java/pom.xml clean package
```
L’artefact sera: `target/p2p-fileshare-1.0.0-shaded.jar`.

## Exécution
```bash
cd p2p-fileshare-java
java -jar target/p2p-fileshare-1.0.0-shaded.jar --port 5000 --share ~/Partage --downloads ~/Téléchargements/P2P
```
> Sur **chaque machine** du même réseau local, lancez l’application (avec un port différent si besoin).

## Tests rapides (une seule machine possible)
1. Lancez 2 instances (ports 5000 et 5001), pointant vers deux dossiers de partage différents.
2. Dans l’UI, sélectionnez un pair découvert, appuyez sur **“Lister fichiers”**, puis téléchargez un fichier.
3. La somme SHA‑256 est vérifiée côté client à la fin du transfert.

## Protocoles
- UDP Multicast: diffusion toutes les 3s du message `HELLO <host> <port>`
- TCP:
  - `LIST\n` ⟶ serveur envoie `ObjectOutputStream` avec `List<FileMetadata>`
  - `GET <filename>\n` ⟶ serveur envoie `long length`, `byte[length]` du fichier + `byte[32]` checksum SHA‑256

## Remarques
- Le **WatchService** observe le dossier partagé et ré-indexe automatiquement.
- Le projet choisit `java.util.logging` (pas de dépendance).

> Projet éducatif : simple et lisible, mais robuste.
