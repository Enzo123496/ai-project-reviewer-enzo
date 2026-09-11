package fr.master.reviewer.selection;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedSelectionStrategyTest {

    @Test
    void keepsRelevantTypesAndAppliesExclusionsAndSizeLimit(@TempDir Path dir) {
        TestData.writeProject(dir, Map.of(
                "src/main/java/B.java", "class B {}",
                "src/main/java/A.java", "class A {}",
                "target/generated/Gen.java", "class Gen {}",
                "src/main/java/Huge.java", "x".repeat(5000),
                "README.md", "doc"));
        Project project = TestData.load(dir);
        RuleBasedSelectionStrategy strategy = new RuleBasedSelectionStrategy(List.of("**/target/**"), 1000);

        List<String> selected = strategy.select(project, TestData.criterion("c", "llm-code"))
                .stream().map(FileNode::relativePath).toList();

        assertEquals(List.of("src/main/java/A.java", "src/main/java/B.java"), selected, "trié, sans target/ ni fichier trop gros");
    }
}
