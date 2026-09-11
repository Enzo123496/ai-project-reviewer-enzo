package fr.master.reviewer.analysis;

import java.util.List;

/**
 * PATTERN OBSERVER : le moteur publie des événements, n'importe qui peut s'abonner
 * (IHM Swing, console, tests). Le moteur ne dépend donc PAS de l'interface graphique.
 * Attention : les méthodes peuvent être appelées depuis un thread de travail.
 */
public interface AnalysisListener {

    default void analysisStarted(String projectName, int criteriaCount) {
    }

    default void criterionStarted(Criterion criterion, int index, int total) {
    }

    default void progress(Criterion criterion, String message) {
    }

    default void criterionFinished(CriterionResult result, int index, int total) {
    }

    default void warning(String message) {
    }

    default void analysisFinished(EvaluationResult result) {
    }

    static AnalysisListener none() {
        return new AnalysisListener() {
        };
    }

    /** Diffuse chaque événement à plusieurs abonnés. */
    static AnalysisListener compose(List<AnalysisListener> listeners) {
        List<AnalysisListener> copy = List.copyOf(listeners);
        return new AnalysisListener() {
            @Override
            public void analysisStarted(String p, int n) {
                copy.forEach(l -> l.analysisStarted(p, n));
            }

            @Override
            public void criterionStarted(Criterion c, int i, int t) {
                copy.forEach(l -> l.criterionStarted(c, i, t));
            }

            @Override
            public void progress(Criterion c, String m) {
                copy.forEach(l -> l.progress(c, m));
            }

            @Override
            public void criterionFinished(CriterionResult r, int i, int t) {
                copy.forEach(l -> l.criterionFinished(r, i, t));
            }

            @Override
            public void warning(String m) {
                copy.forEach(l -> l.warning(m));
            }

            @Override
            public void analysisFinished(EvaluationResult r) {
                copy.forEach(l -> l.analysisFinished(r));
            }
        };
    }
}
