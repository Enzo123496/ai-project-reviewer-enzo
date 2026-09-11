package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.prompt.UntrustedContentGuard;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.selection.RuleBasedSelectionStrategy;
import fr.master.reviewer.testsupport.ScriptedLlmProvider;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationEngineTest {

    @Test
    void crashingAnalyzerIsIsolatedAndObserverIsNotified(@TempDir Path dir) {
        Project project = TestData.load(TestData.writeProject(dir, Map.of("src/main/java/A.java", "class A {}")));
        AnalyzerRegistry registry = new AnalyzerRegistry()
                .register("ok", (c, llm) -> ctx -> new CriterionResult(c.id(), c.name(), 8, 10, List.of("bien"), List.of(),
                        List.of(), List.of(), CriterionResult.Status.OK, "test", List.of(), 0))
                .register("boom", (c, llm) -> ctx -> {
                    throw new IllegalStateException("bug dans l'analyseur");
                });
        EvaluationEngine engine = new EvaluationEngine(registry, new RuleBasedSelectionStrategy(List.of(), 100_000),
                ContentReader.fromDisk(), new SummaryWriter(new UntrustedContentGuard(), "French"), 1);

        List<String> events = new ArrayList<>();
        AnalysisListener listener = new AnalysisListener() {
            @Override
            public void analysisStarted(String p, int n) {
                events.add("start:" + n);
            }

            @Override
            public void criterionFinished(CriterionResult r, int i, int t) {
                events.add(r.criterionId() + ":" + r.status());
            }

            @Override
            public void analysisFinished(EvaluationResult r) {
                events.add("end");
            }
        };
        EvaluationResult result = engine.evaluate(new EvaluationEngine.Session(project,
                List.of(TestData.criterion("a", "ok"), TestData.criterion("b", "boom")), new ScriptedLlmProvider(),
                "test", Map.of(), listener, AnalysisTrace.noop(), false));

        assertEquals(List.of("start:2", "a:OK", "b:FAILED", "end"), events);
        assertEquals(16.0, result.overallScoreOn20(), "le critère en échec est exclu de la moyenne");
        assertTrue(result.summary().contains("1 critère(s) en échec"));
        assertNull(result.llmCommentary());
        assertNotNull(result.metrics());
    }

    @Test
    void notApplicableCriteriaDoNotLowerTheOverallScore(@TempDir Path dir) {
        Project project = TestData.load(TestData.writeProject(dir, Map.of("README.md", "doc")));
        AnalyzerRegistry registry = new AnalyzerRegistry()
                .register("ok", (c, llm) -> ctx -> new CriterionResult(c.id(), c.name(), 8, 10, List.of(), List.of(),
                        List.of(), List.of(), CriterionResult.Status.OK, "test", List.of(), 0))
                .register("na", (c, llm) -> ctx -> CriterionResult.notApplicable(c, "aucun fichier"));
        EvaluationEngine engine = new EvaluationEngine(registry, new RuleBasedSelectionStrategy(List.of(), 100_000),
                ContentReader.fromDisk(), new SummaryWriter(new UntrustedContentGuard(), "French"), 1);

        EvaluationResult result = engine.evaluate(new EvaluationEngine.Session(project,
                List.of(TestData.criterion("a", "ok"), TestData.criterion("b", "na")), new ScriptedLlmProvider(),
                "test", Map.of(), AnalysisListener.none(), AnalysisTrace.noop(), false));

        assertEquals(16.0, result.overallScoreOn20());
        assertTrue(result.summary().contains("non applicable"));
    }

    @Test
    void parallelModeKeepsCriteriaOrder(@TempDir Path dir) {
        Project project = TestData.load(TestData.writeProject(dir, Map.of("README.md", "doc")));
        AnalyzerRegistry registry = new AnalyzerRegistry().register("slow", (c, llm) -> ctx -> {
            try {
                Thread.sleep(c.id().equals("first") ? 150 : 10);   // le premier critère finit en dernier
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new CriterionResult(c.id(), c.name(), 5, 10, List.of(), List.of(), List.of(), List.of(),
                    CriterionResult.Status.OK, "test", List.of(), 0);
        });
        EvaluationEngine engine = new EvaluationEngine(registry, new RuleBasedSelectionStrategy(List.of(), 100_000),
                ContentReader.fromDisk(), new SummaryWriter(new UntrustedContentGuard(), "French"), 3);

        EvaluationResult result = engine.evaluate(new EvaluationEngine.Session(project,
                List.of(TestData.criterion("first", "slow"), TestData.criterion("second", "slow"), TestData.criterion("third", "slow")),
                new ScriptedLlmProvider(), "test", Map.of(), AnalysisListener.none(), AnalysisTrace.noop(), false));

        assertEquals(List.of("first", "second", "third"), result.results().stream().map(CriterionResult::criterionId).toList(),
                "le rapport reste dans l'ordre de la configuration, quel que soit l'ordre de fin des threads");
    }
}
