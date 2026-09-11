package fr.master.reviewer.analysis;

import fr.master.reviewer.project.Project;
import fr.master.reviewer.selection.FileSelectionStrategy;

/** Tout ce dont un analyseur a besoin pour évaluer un critère, regroupé dans un seul objet. */
public record AnalysisContext(Project project, Criterion criterion, ProjectMetrics metrics,
                              FileSelectionStrategy selection, ContentReader reader,
                              AnalysisListener listener, AnalysisTrace trace) {
}
