package fr.master.reviewer.analysis;

import fr.master.reviewer.project.Project;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsCalculatorTest {

    @Test
    void countsDeterministicFacts(@TempDir Path dir) {
        Project project = TestData.load(TestData.writeProject(dir, Map.of(
                "README.md", "doc",
                "src/main/java/Big.java", "class Big {\n void a() { try { x(); } catch (Exception e) { } }\n // TODO nettoyer\n}",
                "src/main/java/Small.java", "class Small {}",
                "src/test/java/BigTest.java", "class BigTest {}")));

        ProjectMetrics m = new MetricsCalculator(ContentReader.fromDisk()).compute(project);

        assertEquals(2, m.javaFiles());
        assertEquals(1, m.testFiles());
        assertEquals(1, m.emptyCatchBlocks());
        assertEquals(1, m.genericCatches());
        assertEquals(1, m.todoCount());
        assertEquals("src/main/java/Big.java", m.largestJavaFile());
        assertTrue(m.hasReadme());
    }
}
