package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileInterleaverTest {

    private static FileNode f(String path) {
        return new FileNode(path, path, Path.of(path), FileType.JAVA_SOURCE, 1);
    }

    @Test
    void takesOneFilePerDirectoryInTurnAndIsDeterministic() {
        List<FileNode> input = List.of(f("a/1.java"), f("a/2.java"), f("a/3.java"), f("b/1.java"), f("c/1.java"), f("c/2.java"));

        List<String> result = FileInterleaver.interleaveByDirectory(input).stream().map(FileNode::relativePath).toList();

        assertEquals(List.of("a/1.java", "b/1.java", "c/1.java", "a/2.java", "c/2.java", "a/3.java"), result);
        assertEquals(result, FileInterleaver.interleaveByDirectory(input).stream().map(FileNode::relativePath).toList());
    }
}
