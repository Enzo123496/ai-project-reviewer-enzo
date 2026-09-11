package fr.master.reviewer.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Feuille du Composite : un fichier du projet analysé. Son contenu est une DONNÉE NON FIABLE. */
public record FileNode(String name, String relativePath, Path absolutePath, FileType type, long size)
        implements ProjectNode {

    @Override
    public List<ProjectNode> children() {
        return List.of();
    }

    @Override
    public Stream<FileNode> files() {
        return Stream.of(this);
    }

    /** Lit le contenu en UTF-8 (octets invalides remplacés). Retourne "" pour un fichier binaire. */
    public String readContent() throws IOException {
        byte[] bytes = Files.readAllBytes(absolutePath);
        if (looksBinary(bytes)) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Heuristique classique : un octet nul dans les premiers 8 Ko indique un fichier binaire. */
    public boolean isBinary() {
        try (InputStream in = Files.newInputStream(absolutePath)) {
            return looksBinary(in.readNBytes(8192));
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean looksBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
