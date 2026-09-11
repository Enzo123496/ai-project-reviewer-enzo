package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerTest {

    private static FileNode file(String path) {
        return new FileNode(path, path, Path.of(path), FileType.JAVA_SOURCE, 10);
    }

    @Test
    void packsSmallFilesTogetherAndRespectsLimits() {
        Map<String, String> contents = Map.of("A.java", "a".repeat(400), "B.java", "b".repeat(400), "C.java", "c".repeat(400));
        Chunker.ChunkPlan plan = new Chunker(1000, 5).plan(List.of(file("A.java"), file("B.java"), file("C.java")),
                f -> contents.get(f.relativePath()));

        assertEquals(2, plan.chunks().size());
        assertEquals(List.of("A.java", "B.java"), plan.chunks().get(0).files());
        assertTrue(plan.chunks().stream().allMatch(c -> c.content().length() <= 1000));
        assertTrue(plan.skippedFiles().isEmpty());
    }

    @Test
    void splitsLargeFileByLinesWithHeaders() {
        String big = "int x;\n".repeat(400); // ~2800 caractères
        Chunker.ChunkPlan plan = new Chunker(1000, 10).plan(List.of(file("Big.java")), f -> big);

        assertTrue(plan.chunks().size() >= 3);
        assertTrue(plan.chunks().get(1).content().contains("Big.java (lines"));
    }

    @Test
    void reportsSkippedFilesWhenChunkBudgetIsExhausted() {
        Chunker.ChunkPlan plan = new Chunker(600, 1).plan(List.of(file("A.java"), file("B.java")), f -> "x".repeat(500));

        assertEquals(1, plan.chunks().size());
        assertEquals(List.of("B.java"), plan.skippedFiles());
    }
}
