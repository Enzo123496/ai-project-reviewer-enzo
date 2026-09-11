package fr.master.reviewer.ui;

import fr.master.reviewer.analysis.AnalysisListener;
import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.analysis.EvaluationResult;

import javax.swing.SwingUtilities;

/**
 * Observateur côté IHM. Règle d'or de Swing : on ne touche aux composants QUE depuis le thread graphique (EDT).
 * Le moteur appelle ces méthodes depuis un thread de travail : chaque événement est donc "reposté" sur l'EDT.
 */
final class SwingAnalysisListener implements AnalysisListener {

    private final MainWindow window;

    SwingAnalysisListener(MainWindow window) {
        this.window = window;
    }

    @Override
    public void analysisStarted(String projectName, int criteriaCount) {
        SwingUtilities.invokeLater(() -> window.onAnalysisStarted(criteriaCount));
    }

    @Override
    public void criterionStarted(Criterion c, int index, int total) {
        SwingUtilities.invokeLater(() -> window.log("[" + index + "/" + total + "] " + c.name() + "..."));
    }

    @Override
    public void progress(Criterion c, String message) {
        SwingUtilities.invokeLater(() -> window.log("    " + message));
    }

    @Override
    public void criterionFinished(CriterionResult r, int index, int total) {
        SwingUtilities.invokeLater(() -> window.onCriterionFinished(r, index));
    }

    @Override
    public void warning(String message) {
        SwingUtilities.invokeLater(() -> window.error(message));
    }

    @Override
    public void analysisFinished(EvaluationResult result) {
        // La fin est traitée par le CompletableFuture dans MainWindow.
    }
}
