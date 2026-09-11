package fr.master.reviewer.project;

import java.util.List;
import java.util.stream.Stream;

/** Nœud composite : un dossier contenant d'autres nœuds. */
public record DirectoryNode(String name, String relativePath, List<ProjectNode> children) implements ProjectNode {

    public DirectoryNode {
        children = List.copyOf(children);
    }

    @Override
    public Stream<FileNode> files() {
        // Récursion naturelle du Composite : on délègue à chaque enfant.
        return children.stream().flatMap(ProjectNode::files);
    }
}
