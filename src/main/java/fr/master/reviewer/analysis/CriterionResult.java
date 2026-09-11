package fr.master.reviewer.analysis;

import java.util.List;

/**
 * Résultat d'un critère. C'est une représentation Java PURE : elle ne sait rien de LaTeX ni du LLM.
 * Le rapport (LaTeX, HTML...) est produit à partir de ces objets.
 */
public record CriterionResult(String criterionId, String criterionName, double score, int maxScore,
                              List<String> strengths, List<String> weaknesses, List<String> issues,
                              List<String> recommendations, Status status, String source,
                              List<String> warnings, long durationMs) {

    /**
     * OK = complet ; PARTIAL = une partie des segments a échoué ; FAILED = aucune note exploitable ;
     * NOT_APPLICABLE = rien à évaluer (ex. aucun fichier Java). FAILED et NOT_APPLICABLE sont exclus de la note globale.
     */
    public enum Status { OK, PARTIAL, FAILED, NOT_APPLICABLE;

        /** Indique si le résultat porte une note qui doit compter dans la moyenne. */
        public boolean isScored() {
            return this == OK || this == PARTIAL;
        }
    }

    public CriterionResult {
        strengths = List.copyOf(strengths == null ? List.of() : strengths);
        weaknesses = List.copyOf(weaknesses == null ? List.of() : weaknesses);
        issues = List.copyOf(issues == null ? List.of() : issues);
        recommendations = List.copyOf(recommendations == null ? List.of() : recommendations);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        if (status.isScored() && (score < 0 || score > maxScore)) {
            throw new IllegalArgumentException("Score hors bornes : " + score + "/" + maxScore);
        }
    }

    public static CriterionResult failed(Criterion c, String reason, String source) {
        return new CriterionResult(c.id(), c.name(), 0, c.maxScore(), List.of(), List.of(),
                List.of("Analyse impossible : " + reason), List.of(), Status.FAILED, source, List.of(), 0);
    }

    public static CriterionResult notApplicable(Criterion c, String reason) {
        return new CriterionResult(c.id(), c.name(), 0, c.maxScore(), List.of(), List.of(), List.of(), List.of(),
                Status.NOT_APPLICABLE, "non applicable : " + reason, List.of(), 0);
    }

    public CriterionResult withAdditionalWarnings(List<String> extra) {
        List<String> all = new java.util.ArrayList<>(warnings);
        all.addAll(extra);
        return new CriterionResult(criterionId, criterionName, score, maxScore, strengths, weaknesses, issues,
                recommendations, status, source, all, durationMs);
    }

    public CriterionResult withDuration(long ms) {
        return new CriterionResult(criterionId, criterionName, score, maxScore, strengths, weaknesses, issues,
                recommendations, status, source, warnings, ms);
    }

    public double ratio() {
        return maxScore == 0 ? 0 : score / maxScore;
    }
}
