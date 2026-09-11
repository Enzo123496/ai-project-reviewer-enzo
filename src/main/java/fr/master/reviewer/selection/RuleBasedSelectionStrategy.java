package fr.master.reviewer.selection;

import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.Project;

import java.util.Comparator;
import java.util.List;

/**
 * Stratégie par défaut : types pertinents du critère, moins les exclusions (globs), moins les fichiers
 * trop gros ou binaires. Tri déterministe (type puis chemin) => même projet = mêmes segments = rapport
 * reproductible.
 */
public final class RuleBasedSelectionStrategy implements FileSelectionStrategy {

    private final List<String> excludeGlobs;
    private final FileRule exclusions;
    private final long maxFileSizeBytes;

    public RuleBasedSelectionStrategy(List<String> excludeGlobs, long maxFileSizeBytes) {
        this.excludeGlobs = List.copyOf(excludeGlobs);
        this.maxFileSizeBytes = maxFileSizeBytes;
        FileRule rule = f -> false;
        for (String g : excludeGlobs) {
            rule = rule.or(FileRule.glob(g));
        }
        this.exclusions = rule;
    }

    @Override
    public List<FileNode> select(Project project, Criterion criterion) {
        FileRule keep = FileRule.ofTypes(criterion.fileTypes())
                .and(exclusions.negate())
                .and(FileRule.maxSize(maxFileSizeBytes));
        return project.files()
                .filter(keep::matches)
                .filter(f -> !f.isBinary())
                .sorted(Comparator.comparing((FileNode f) -> f.type().ordinal()).thenComparing(FileNode::relativePath))
                .toList();
    }

    @Override
    public String name() {
        return "règles (exclusions: " + excludeGlobs.size() + ", taille max: " + maxFileSizeBytes / 1024 + " Ko)";
    }
}
