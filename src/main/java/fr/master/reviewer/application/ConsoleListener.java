package fr.master.reviewer.application;

import fr.master.reviewer.analysis.AnalysisListener;
import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.analysis.EvaluationResult;

import java.io.PrintStream;

/** Observateur pour la ligne de commande : affiche la progression dans le terminal. */
public final class ConsoleListener implements AnalysisListener {

    private final PrintStream out;

    public ConsoleListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public void analysisStarted(String projectName, int criteriaCount) {
        out.println("Analyse de '" + projectName + "' : " + criteriaCount + " critère(s)");
    }

    @Override
    public void criterionStarted(Criterion c, int index, int total) {
        out.println("[" + index + "/" + total + "] " + c.name() + "...");
    }

    @Override
    public void progress(Criterion c, String message) {
        out.println("      " + message);
    }

    @Override
    public void criterionFinished(CriterionResult r, int index, int total) {
        out.printf("      -> %s/%d (%s, %d ms)%n", r.score(), r.maxScore(), r.status(), r.durationMs());
    }

    @Override
    public void warning(String message) {
        out.println("  ! " + message);
    }

    @Override
    public void analysisFinished(EvaluationResult result) {
        out.printf("Note globale : %.1f/20 - %d appel(s) LLM, %d échec(s), %d nouvelle(s) tentative(s)%n",
                result.overallScoreOn20(), result.trace().llmCalls(), result.trace().llmFailures(), result.trace().retries());
    }
}
