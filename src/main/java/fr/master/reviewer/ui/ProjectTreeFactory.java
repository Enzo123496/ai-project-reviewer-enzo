package fr.master.reviewer.ui;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.project.ProjectNode;

import javax.swing.tree.DefaultMutableTreeNode;

/** Convertit le Composite du projet en arbre Swing. Grâce au Composite : une seule méthode récursive. */
final class ProjectTreeFactory {

    private ProjectTreeFactory() {
    }

    static DefaultMutableTreeNode build(Project project) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(project.name() + "  (" + project.files().count() + " fichiers)");
        project.root().children().forEach(child -> root.add(toSwing(child)));
        return root;
    }

    private static DefaultMutableTreeNode toSwing(ProjectNode node) {
        if (node instanceof FileNode f) {
            return new DefaultMutableTreeNode(f.name() + "   [" + f.type().label() + "]");
        }
        DefaultMutableTreeNode dir = new DefaultMutableTreeNode(node.name() + "/");
        node.children().forEach(child -> dir.add(toSwing(child)));
        return dir;
    }
}
