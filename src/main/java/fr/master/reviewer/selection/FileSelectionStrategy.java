package fr.master.reviewer.selection;

import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.Project;

import java.util.List;

/**
 * PATTERN STRATEGY : décide quels fichiers sont envoyés pour un critère.
 * On peut imaginer d'autres stratégies (fichiers modifiés récemment, les plus gros, par package...).
 */
public interface FileSelectionStrategy {

    List<FileNode> select(Project project, Criterion criterion);

    String name();
}
