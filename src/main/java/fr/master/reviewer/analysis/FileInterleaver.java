package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RÉPARTITION ÉQUITABLE : quand tout le code ne tient pas dans le budget, envoyer les fichiers par ordre
 * alphabétique ne montre au LLM que les premiers packages (mesuré sur notre projet : seulement 2 dossiers
 * sur 14 lus). On prend donc un fichier par dossier, à tour de rôle ("round-robin"). L'ordre reste
 * déterministe : même projet, même échantillon, donc cache et reproductibilité préservés.
 */
public final class FileInterleaver {

    private FileInterleaver() {
    }

    public static List<FileNode> interleaveByDirectory(List<FileNode> files) {
        Map<String, Deque<FileNode>> byDirectory = new LinkedHashMap<>();
        for (FileNode f : files) {
            byDirectory.computeIfAbsent(directoryOf(f), d -> new ArrayDeque<>()).add(f);
        }
        List<FileNode> result = new ArrayList<>(files.size());
        while (result.size() < files.size()) {
            for (Deque<FileNode> queue : byDirectory.values()) {
                if (!queue.isEmpty()) {
                    result.add(queue.poll());
                }
            }
        }
        return result;
    }

    public static String directoryOf(FileNode f) {
        int i = f.relativePath().lastIndexOf('/');
        return i < 0 ? "" : f.relativePath().substring(0, i);
    }
}
