package fr.master.reviewer.project;

import java.util.List;
import java.util.stream.Stream;

/**
 * PATTERN COMPOSITE : un dossier (DirectoryNode) et un fichier (FileNode) partagent la même interface.
 * Le code client (arbre de l'IHM, sélection des fichiers, métriques) parcourt l'arborescence
 * sans se demander à chaque niveau "est-ce un dossier ou un fichier ?".
 */
public sealed interface ProjectNode permits DirectoryNode, FileNode {

    String name();

    /** Chemin relatif à la racine du projet, séparateur '/', vide pour la racine. */
    String relativePath();

    List<ProjectNode> children();

    /** Tous les fichiers contenus dans ce nœud (lui-même s'il s'agit d'un fichier). */
    Stream<FileNode> files();

    default boolean isDirectory() {
        return this instanceof DirectoryNode;
    }

    default long fileCount() {
        return files().count();
    }
}
