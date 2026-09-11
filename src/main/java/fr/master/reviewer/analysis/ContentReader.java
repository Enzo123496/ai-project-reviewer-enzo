package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;

import java.io.IOException;

/**
 * Abstraction de la lecture d'un fichier. En production : lecture disque ; en test : contenu en mémoire.
 * Un fichier illisible donne "" : il ne doit pas faire échouer toute l'analyse.
 */
@FunctionalInterface
public interface ContentReader {

    String read(FileNode file);

    static ContentReader fromDisk() {
        return file -> {
            try {
                return file.readContent();
            } catch (IOException e) {
                return "";
            }
        };
    }
}
