package fr.master.reviewer.application;

import fr.master.reviewer.analysis.AnalysisListener;
import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.analysis.EvaluationResult;
import fr.master.reviewer.config.AppConfig;
import fr.master.reviewer.persistence.InMemoryHistoryRepository;
import fr.master.reviewer.project.FileType;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.testsupport.ScriptedLlmProvider;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test d'intégration de bout en bout, SANS interface graphique et SANS vrai LLM. */
class ReviewerFacadeTest {

    static AppConfig config(Path reports, String active, String apiKeyEnv) {
        return new AppConfig(
                new AppConfig.LlmSettings(active, null, 2, 1, false, 0, 500, "French", List.of(
                        new AppConfig.ProviderSettings("fake", "scripted", null, "fake-model", apiKeyEnv, null, 0),
                        new AppConfig.ProviderSettings("mock", "mock", null, null, null, null, 0))),
                new AppConfig.ContextSettings(8000, 3, 1),
                new AppConfig.SelectionSettings(List.of("**/target/**"), 100_000),
                null,
                new AppConfig.ReportSettings(reports.toString(), "none", null),
                List.of(new Criterion("readability", "Lisibilité", "d", 10, "llm-code", Set.of(FileType.JAVA_SOURCE), 1),
                        new Criterion("tests", "Tests", "d", 10, "test-presence", null, 1)),
                List.of(new AppConfig.Profile("complet", "", List.of("readability", "tests"))));
    }

    @Test
    void importAnalyzeReportAndHistory(@TempDir Path dir) throws Exception {
        Path projectDir = TestData.writeProject(dir.resolve("p"), Map.of("src/main/java/A.java", "class A {}"));
        ScriptedLlmProvider llm = new ScriptedLlmProvider().otherwiseAnswer(ScriptedLlmProvider.validJson("readability", 7));
        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        ReviewerFacade facade = AppFactory.build(config(dir.resolve("reports"), "fake", null), name -> null, dir.resolve("work"),
                history, f -> f.registerType("scripted", (settings, key) -> llm));

        Project project = facade.loadProject(projectDir.toString());
        EvaluationResult result = facade.analyze(new AnalysisRequest(project, "complet", List.of(), null, true), AnalysisListener.none());

        assertEquals(2, result.results().size());
        assertEquals(7.0, result.results().get(0).score());
        assertEquals(CriterionResult.Status.OK, result.results().get(1).status());
        assertTrue(result.trace().llmCalls() >= 2, "évaluation + commentaire");
        assertEquals(1, facade.history().size());

        Path tex = facade.generateReport(result, "latex");
        assertTrue(Files.readString(tex).contains("\\section{Tableau récapitulatif}"));
        assertEquals("evaluation.tex", tex.getFileName().toString());
    }

    @Test
    void missingApiKeyIsReportedBeforeAnalysis(@TempDir Path dir) throws Exception {
        Path projectDir = TestData.writeProject(dir.resolve("p"), Map.of("A.java", "class A {}"));
        ReviewerFacade facade = AppFactory.build(config(dir, "fake", "MISTRAL_API_KEY"), name -> null, dir.resolve("work"),
                new InMemoryHistoryRepository(), f -> f.registerType("scripted", (s, k) -> new ScriptedLlmProvider()));
        Project project = facade.loadProject(projectDir.toString());

        ReviewerException e = assertThrows(ReviewerException.class,
                () -> facade.analyze(new AnalysisRequest(project, "complet", List.of(), null, false), AnalysisListener.none()));
        assertTrue(e.getMessage().contains("MISTRAL_API_KEY"));
    }
}
