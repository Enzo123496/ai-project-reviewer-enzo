package fr.master.reviewer.analysis;

import java.util.List;
import java.util.Map;

/**
 * Résultat complet d'une analyse : c'est le "modèle" à partir duquel TOUS les rapports sont produits.
 * - summary       : synthèse DÉTERMINISTE, toujours présente (la structure du rapport ne dépend pas du LLM)
 * - llmCommentary : commentaire rédigé par le LLM, optionnel (null si indisponible)
 * Les dates sont en ISO-8601 (texte) pour rester lisibles dans l'historique JSON.
 */
public record EvaluationResult(String analysisId, String projectName, String projectSource, String startedAt,
                               String finishedAt, String profileName, String llmDescription,
                               Map<String, String> configuration, List<CriterionResult> results,
                               double overallScoreOn20, ProjectMetrics metrics, String summary,
                               String llmCommentary, TraceSummary trace) {

    public EvaluationResult {
        configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
        results = List.copyOf(results == null ? List.of() : results);
    }

    /** Moyenne pondérée des critères notés (OK ou PARTIAL), ramenée sur 20. FAILED et NOT_APPLICABLE ne comptent pas comme 0. */
    public static double computeOverallOn20(List<CriterionResult> results, List<Criterion> criteria) {
        double weighted = 0;
        double weights = 0;
        for (CriterionResult r : results) {
            if (!r.status().isScored()) {
                continue;
            }
            double w = criteria.stream().filter(c -> c.id().equals(r.criterionId()))
                    .mapToDouble(Criterion::weight).findFirst().orElse(1.0);
            weighted += r.ratio() * w;
            weights += w;
        }
        return weights == 0 ? 0 : Math.round(weighted / weights * 20 * 10) / 10.0;
    }

    public long failedCount() {
        return results.stream().filter(r -> r.status() == CriterionResult.Status.FAILED).count();
    }

    public EvaluationResult withSummaries(String newSummary, String commentary) {
        return new EvaluationResult(analysisId, projectName, projectSource, startedAt, finishedAt, profileName,
                llmDescription, configuration, results, overallScoreOn20, metrics, newSummary, commentary, trace);
    }
}
