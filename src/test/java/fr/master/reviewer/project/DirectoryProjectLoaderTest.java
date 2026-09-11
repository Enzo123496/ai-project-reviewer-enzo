package fr.master.reviewer.project;

import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryProjectLoaderTest {

    private final DirectoryProjectLoader loader = new DirectoryProjectLoader(FileClassifier.defaultClassifier());

    @Test
    void buildsCompositeTreeAndClassifiesFiles(@TempDir Path dir) throws Exception {
        TestData.writeProject(dir, Map.of(
                "pom.xml", "<project/>",
                "src/main/java/a/App.java", "class App {}",
                "src/test/java/a/AppTest.java", "class AppTest {}",
                ".git/config", "secret"));

        Project project = loader.load(dir.toString());

        assertEquals(3, project.files().count(), ".git doit être ignoré");
        assertEquals(1L, project.countByType().get(FileType.JAVA_SOURCE));
        assertEquals(1L, project.countByType().get(FileType.TEST));
        assertTrue(project.root().children().stream().anyMatch(ProjectNode::isDirectory));
    }

    @Test
    void doesNotFollowSymbolicLinks(@TempDir Path dir, @TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("id_rsa"), "PRIVATE");
        TestData.writeProject(dir, Map.of("A.java", "class A {}"));
        try {
            Files.createSymbolicLink(dir.resolve("leak"), outside);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // système sans liens symboliques : test non applicable
        }
        Project project = loader.load(dir.toString());
        assertFalse(project.files().anyMatch(f -> f.name().equals("id_rsa")));
    }

    @Test
    void rejectsMissingDirectory(@TempDir Path dir) {
        assertThrows(ProjectLoadException.class, () -> loader.load(dir.resolve("absent"), "x", "x"));
    }
}
