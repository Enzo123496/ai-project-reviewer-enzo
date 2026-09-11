package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerPartialTest {

    private static FileNode file(String path) {
        return new FileNode(path, path, Path.of(path), FileType.JAVA_SOURCE, 10);
    }

    @Test
    void distinguishesPartiallySentFromSkipped() {
        String big = "int x;\n".repeat(400);   // découpé en plusieurs morceaux
        Chunker.ChunkPlan plan = new Chunker(1000, 2).plan(List.of(file("Big.java"), file("Other.java")), f -> big);

        assertEquals(List.of("Big.java"), plan.partiallySentFiles(), "début envoyé, fin coupée");
        assertEquals(List.of("Other.java"), plan.skippedFiles(), "rien envoyé");
        assertEquals(1, plan.sentFileCount());
        assertTrue(plan.truncated());
    }
}
