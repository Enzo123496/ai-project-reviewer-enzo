package fr.master.reviewer.persistence;

/** Ligne de l'historique (vue résumée d'une analyse). */
public record HistoryEntry(String analysisId, String projectName, String startedAt, String profileName,
                           String llmDescription, double overallScoreOn20, long failedCriteria) {
}
