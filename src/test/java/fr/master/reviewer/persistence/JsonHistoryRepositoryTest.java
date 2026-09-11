package fr.master.reviewer.persistence;

import fr.master.reviewer.analysis.EvaluationResult;
import fr.master.reviewer.report.ReportGenerationTestAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonHistoryRepositoryTest {

    @Test
    void savesAndReloadsResults(@TempDir Path dir) throws Exception {
        JsonHistoryRepository repo = new JsonHistoryRepository(dir);
        EvaluationResult original = ReportGenerationTestAccess.sample();
        repo.save(original);
        Files.writeString(dir.resolve("corrompu.json"), "{pas du json");

        assertEquals(1, repo.list().size(), "un fichier corrompu est ignoré");
        EvaluationResult reloaded = repo.find("abc123").orElseThrow();
        assertEquals(original, reloaded);
        assertTrue(repo.list().get(0).overallScoreOn20() > 0);
    }
}
