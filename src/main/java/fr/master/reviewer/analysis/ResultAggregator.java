package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.parsing.LlmEvaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * AGRÉGATION ("reduce") : combine les évaluations de plusieurs segments en un seul résultat.
 * Note = moyenne PONDÉRÉE par la taille des segments (un segment de 300 caractères ne pèse pas autant qu'un
 * segment de 12 000) ; listes fusionnées et dédoublonnées ; écart important entre segments => avertissement.
 */
public final class ResultAggregator {

    /** Une évaluation et son poids (taille du contenu évalué, en caractères). */
    public record Part(LlmEvaluation evaluation, int weight) {
        public Part {
            weight = Math.max(1, weight);
        }
    }

    private static final int MAX_PER_LIST = 6;

    public CriterionResult aggregate(Criterion criterion, List<Part> weightedParts, int failedSegments,
                                     List<String> warnings, List<String> injectionFindings, String source) {
        if (weightedParts.isEmpty()) {
            throw new IllegalArgumentException("Aucune évaluation à agréger");
        }
        List<LlmEvaluation> parts = weightedParts.stream().map(Part::evaluation).toList();
        long totalWeight = weightedParts.stream().mapToLong(Part::weight).sum();
        double average = weightedParts.stream().mapToDouble(p -> p.evaluation().score() * p.weight()).sum() / totalWeight;
        double min = parts.stream().mapToDouble(LlmEvaluation::score).min().orElse(0);
        double max = parts.stream().mapToDouble(LlmEvaluation::score).max().orElse(0);

        List<String> allWarnings = new ArrayList<>(warnings);
        parts.forEach(p -> allWarnings.addAll(p.warnings()));
        if (parts.size() > 1 && max - min > criterion.maxScore() * 0.5) {
            allWarnings.add("Notes très différentes selon les segments (" + min + " à " + max
                    + ") : résultat à vérifier manuellement");
        }
        List<String> issues = new ArrayList<>();
        injectionFindings.forEach(f -> issues.add("Tentative possible d'injection de prompt : " + f));
        issues.addAll(merge(parts, LlmEvaluation::issues));
        if (!injectionFindings.isEmpty() && average >= criterion.maxScore() * 0.9) {
            allWarnings.add("Note très élevée alors qu'une injection a été détectée : vérification humaine recommandée");
        }

        double score = Math.round(average * 10) / 10.0;
        return new CriterionResult(criterion.id(), criterion.name(), score, criterion.maxScore(),
                merge(parts, LlmEvaluation::strengths), merge(parts, LlmEvaluation::weaknesses),
                dedupe(issues, MAX_PER_LIST + injectionFindings.size()), merge(parts, LlmEvaluation::recommendations),
                failedSegments > 0 ? CriterionResult.Status.PARTIAL : CriterionResult.Status.OK,
                source, dedupe(allWarnings, 20), 0);
    }

    private static List<String> merge(List<LlmEvaluation> parts, Function<LlmEvaluation, List<String>> field) {
        List<String> all = new ArrayList<>();
        parts.forEach(p -> all.addAll(field.apply(p)));
        return dedupe(all, MAX_PER_LIST);
    }

    static List<String> dedupe(List<String> values, int limit) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String v : values) {
            unique.putIfAbsent(v.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s\u2018\u2019\u201C\u201D\u00AB\u00BB\u2026]+", " ").strip(), v);
        }
        return unique.values().stream().limit(limit).toList();
    }
}
