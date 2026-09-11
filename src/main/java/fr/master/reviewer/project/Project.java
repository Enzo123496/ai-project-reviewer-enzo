package fr.master.reviewer.project;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Représentation en mémoire d'un projet importé. Aucun code du projet n'est exécuté pour la construire. */
public record Project(String name, String source, Path rootPath, DirectoryNode root) {

    public Stream<FileNode> files() {
        return root.files();
    }

    public Map<FileType, Long> countByType() {
        return files().collect(Collectors.groupingBy(FileNode::type, Collectors.counting()));
    }
}
