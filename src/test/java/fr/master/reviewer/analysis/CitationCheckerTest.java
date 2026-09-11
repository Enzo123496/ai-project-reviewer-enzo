package fr.master.reviewer.analysis;

import fr.master.reviewer.project.Project;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitationCheckerTest {

    @Test
    void reportsOnlyFilesThatDoNotExist(@TempDir Path dir) {
        Project project = TestData.load(TestData.writeProject(dir, Map.of(
                "src/main/java/com/x/BankService.java", "class BankService {}",
                "pom.xml", "<project/>")));

        List<String> unknown = new CitationChecker().unknownCitations(project, List.of(
                "BankService.java mélange logique et accès aux données",
                "Voir com/x/BankService.java et pom.xml",
                "UserService.java et config/app.yml sont absents",
                "Utiliser java.util.List et System.out n'est pas un fichier, 10/10 non plus"));

        assertEquals(List.of("UserService.java", "config/app.yml"), unknown);
    }
}
