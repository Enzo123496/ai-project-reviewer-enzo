package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;

import java.util.ArrayList;
import java.util.List;

/**
 * GESTION DU CONTEXTE : un LLM n'accepte qu'une quantité limitée de texte par requête.
 * On découpe les fichiers sélectionnés en segments de taille bornée (≈ maxChars caractères, soit
 * environ maxChars/4 jetons), en gardant les fichiers entiers quand c'est possible, et on s'arrête à
 * maxChunks segments pour borner le nombre d'appels. Les fichiers non envoyés sont listés (transparence).
 */
public final class Chunker {

    public record Chunk(int index, List<String> files, String content) {
    }

    /**
     * @param skippedFiles       fichiers dont RIEN n'a été envoyé
     * @param partiallySentFiles fichiers dont seul le début a été envoyé (le budget s'est épuisé au milieu)
     */
    public record ChunkPlan(List<Chunk> chunks, List<String> skippedFiles, List<String> partiallySentFiles) {
        public boolean truncated() {
            return !skippedFiles.isEmpty() || !partiallySentFiles.isEmpty();
        }

        /** Nombre de fichiers distincts présents (entiers ou en partie) dans au moins un segment. */
        public long sentFileCount() {
            return chunks.stream().flatMap(c -> c.files().stream()).distinct().count();
        }
    }

    private final int maxChars;
    private final int maxChunks;

    public Chunker(int maxChars, int maxChunks) {
        if (maxChars < 500 || maxChunks < 1) {
            throw new IllegalArgumentException("maxChars >= 500 et maxChunks >= 1 requis");
        }
        this.maxChars = maxChars;
        this.maxChunks = maxChunks;
    }

    public ChunkPlan plan(List<FileNode> files, ContentReader reader) {
        List<Chunk> chunks = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        List<String> currentFiles = new ArrayList<>();

        for (FileNode file : files) {
            if (chunks.size() >= maxChunks) {
                skipped.add(file.relativePath());
                continue;
            }
            String content = reader.read(file);
            if (content.isBlank()) {
                continue;
            }
            boolean started = false;
            for (String piece : splitFile(file.relativePath(), content)) {
                if (current.length() + piece.length() > maxChars && current.length() > 0) {
                    chunks.add(new Chunk(chunks.size() + 1, List.copyOf(currentFiles), current.toString()));
                    current.setLength(0);
                    currentFiles.clear();
                }
                if (chunks.size() >= maxChunks) {
                    // Budget épuisé au milieu de ce fichier : on distingue "rien envoyé" de "début envoyé".
                    (started ? partial : skipped).add(file.relativePath());
                    break;
                }
                current.append(piece);
                started = true;
                if (!currentFiles.contains(file.relativePath())) {
                    currentFiles.add(file.relativePath());
                }
            }
        }
        if (current.length() > 0 && chunks.size() < maxChunks) {
            chunks.add(new Chunk(chunks.size() + 1, List.copyOf(currentFiles), current.toString()));
        }
        return new ChunkPlan(chunks, skipped, partial);
    }

    /** Un fichier trop long est coupé par lignes en plusieurs morceaux, chacun avec un en-tête explicite. */
    List<String> splitFile(String path, String content) {
        String header = "// ===== FILE: " + path + " =====\n";
        if (header.length() + content.length() + 1 <= maxChars) {
            return List.of(header + content + "\n");
        }
        List<String> pieces = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        int budget = maxChars - 120;
        StringBuilder piece = new StringBuilder();
        int firstLine = 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].length() > budget ? lines[i].substring(0, budget) : lines[i];
            if (piece.length() + line.length() + 1 > budget && piece.length() > 0) {
                pieces.add(partHeader(path, firstLine, i) + piece);
                piece.setLength(0);
                firstLine = i + 1;
            }
            piece.append(line).append('\n');
        }
        if (piece.length() > 0) {
            pieces.add(partHeader(path, firstLine, lines.length) + piece);
        }
        return pieces;
    }

    private static String partHeader(String path, int from, int to) {
        return "// ===== FILE: " + path + " (lines " + from + "-" + to + ") =====\n";
    }
}
