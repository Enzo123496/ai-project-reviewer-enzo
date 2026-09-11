package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.LlmProvider;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.selection.FileSelectionStrategy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Moteur d'évaluation : orchestre les critères, sans savoir COMMENT chacun est évalué (Strategy).
 * Reprise partielle : si un critère plante, il est marqué FAILED et les autres continuent.
 */
public final class EvaluationEngine {

    /** Paramètres d'une exécution du moteur. */
    public record Session(Project project, List<Criterion> criteria, LlmProvider llm, String profileName,
                          Map<String, String> configuration, AnalysisListener listener, AnalysisTrace trace,
                          boolean llmCommentary) {
    }

    private final AnalyzerRegistry registry;
    private final FileSelectionStrategy selection;
    private final ContentReader reader;
    private final SummaryWriter summaryWriter;
    private final int parallelism;

    public EvaluationEngine(AnalyzerRegistry registry, FileSelectionStrategy selection, ContentReader reader,
                            SummaryWriter summaryWriter, int parallelism) {
        this.registry = registry;
        this.selection = selection;
        this.reader = reader;
        this.summaryWriter = summaryWriter;
        this.parallelism = Math.max(1, parallelism);
    }

    public EvaluationResult evaluate(Session s) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Instant start = Instant.now();
        s.listener().analysisStarted(s.project().name(), s.criteria().size());
        s.trace().info("Début de l'analyse " + id + " du projet " + s.project().name() + " avec " + s.llm().name()
                + " (" + s.criteria().size() + " critères)");

        ProjectMetrics metrics = new MetricsCalculator(reader).compute(s.project());
        List<CriterionResult> results = parallelism > 1 ? runParallel(s, metrics) : runSequential(s, metrics);

        double overall = EvaluationResult.computeOverallOn20(results, s.criteria());
        String summary = summaryWriter.deterministic(results, overall);
        String commentary = s.llmCommentary() ? summaryWriter.llmCommentary(results, s.llm(), s.trace()) : null;
        s.trace().info("Fin de l'analyse " + id + " : " + overall + "/20");

        EvaluationResult result = new EvaluationResult(id, s.project().name(), s.project().source(),
                start.truncatedTo(ChronoUnit.SECONDS).toString(), Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                s.profileName(), s.llm().name(), s.configuration(), results, overall, metrics, summary, commentary,
                s.trace().summary());
        s.listener().analysisFinished(result);
        return result;
    }

    private List<CriterionResult> runSequential(Session s, ProjectMetrics metrics) {
        List<CriterionResult> results = new ArrayList<>();
        for (int i = 0; i < s.criteria().size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                s.trace().error("Analyse annulée");
                break;
            }
            results.add(runOne(s, s.criteria().get(i), metrics, i + 1));
        }
        return results;
    }

    private List<CriterionResult> runParallel(Session s, ProjectMetrics metrics) {
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<CriterionResult>> futures = new ArrayList<>();
            for (int i = 0; i < s.criteria().size(); i++) {
                Criterion c = s.criteria().get(i);
                int index = i + 1;
                futures.add(pool.submit(() -> runOne(s, c, metrics, index)));
            }
            List<CriterionResult> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (ExecutionException e) {
                    results.add(CriterionResult.failed(s.criteria().get(i), String.valueOf(e.getCause()), "moteur"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private CriterionResult runOne(Session s, Criterion c, ProjectMetrics metrics, int index) {
        int total = s.criteria().size();
        s.listener().criterionStarted(c, index, total);
        long t0 = System.currentTimeMillis();
        CriterionResult result;
        try {
            Analyzer analyzer = registry.create(c, s.llm());
            result = analyzer.analyze(new AnalysisContext(s.project(), c, metrics, selection, reader, s.listener(), s.trace()));
        } catch (RuntimeException e) {
            // Bug ou cas imprévu dans UN analyseur : on isole l'échec au lieu de perdre toute l'analyse.
            s.trace().error("Critère " + c.id() + " : erreur inattendue " + e);
            result = CriterionResult.failed(c, e.getClass().getSimpleName() + " : " + e.getMessage(), "moteur");
        }
        result = result.withDuration(System.currentTimeMillis() - t0);
        s.trace().info("Critère " + c.id() + " terminé : " + result.status() + " " + result.score() + "/" + result.maxScore());
        s.listener().criterionFinished(result, index, total);
        return result;
    }
}
