package fr.master.reviewer.report;

import fr.master.reviewer.analysis.EvaluationResult;

/** Donne accès à l'exemple de résultat aux tests d'autres packages. */
public final class ReportGenerationTestAccess {
    private ReportGenerationTestAccess() {
    }

    public static EvaluationResult sample() {
        return ReportGenerationTest.sample("Couplage fort");
    }
}
