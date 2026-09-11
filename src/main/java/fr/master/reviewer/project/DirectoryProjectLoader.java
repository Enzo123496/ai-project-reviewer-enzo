package fr.master.reviewer.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Charge un projet depuis un dossier local.
 * Sécurité : les liens symboliques ne sont PAS suivis (un projet malveillant pourrait pointer vers
 * ~/.ssh ou /etc), la profondeur et le nombre de fichiers sont bornés.
 */
public final class DirectoryProjectLoader implements ProjectLoader {

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(".git", "node_modules", ".idea", ".gradle");

    private final FileClassifier classifier;
    private final int maxDepth;
    private final int maxFiles;

    public DirectoryProjectLoader(FileClassifier classifier) {
        this(classifier, 40, 50_000);
    }

    public DirectoryProjectLoader(FileClassifier classifier, int maxDepth, int maxFiles) {
        this.classifier = classifier;
        this.maxDepth = maxDepth;
        this.maxFiles = maxFiles;
    }

    @Override
    public boolean supports(String source) {
        try {
            return Files.isDirectory(Path.of(source), LinkOption.NOFOLLOW_LINKS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public Project load(String source) throws ProjectLoadException {
        Path root = Path.of(source).toAbsolutePath().normalize();
        return load(root, root.getFileName() == null ? "projet" : root.getFileName().toString(), source);
    }

    /** Utilisé aussi par les chargeurs ZIP et Git après extraction dans un dossier temporaire. */
    public Project load(Path root, String projectName, String originalSource) throws ProjectLoadException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectLoadException("Dossier introuvable : " + root);
        }
        int[] counter = {0};
        try {
            DirectoryNode tree = walk(root, root, 0, counter);
            return new Project(projectName, originalSource, root, tree);
        } catch (IOException e) {
            throw new ProjectLoadException("Lecture impossible du projet : " + e.getMessage(), e);
        }
    }

    private DirectoryNode walk(Path root, Path dir, int depth, int[] counter) throws IOException, ProjectLoadException {
        if (depth > maxDepth) {
            throw new ProjectLoadException("Arborescence trop profonde (> " + maxDepth + ")");
        }
        List<ProjectNode> children = new ArrayList<>();
        List<Path> entries;
        try (Stream<Path> s = Files.list(dir)) {
            entries = s.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        for (Path entry : entries) {
            if (Files.isSymbolicLink(entry)) {
                continue; // jamais suivre un lien symbolique
            }
            String name = entry.getFileName().toString();
            String rel = root.relativize(entry).toString().replace('\\', '/');
            if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                if (!SKIPPED_DIRECTORIES.contains(name)) {
                    children.add(walk(root, entry, depth + 1, counter));
                }
            } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                if (++counter[0] > maxFiles) {
                    throw new ProjectLoadException("Trop de fichiers (> " + maxFiles + ")");
                }
                children.add(new FileNode(name, rel, entry, classifier.classify(rel), Files.size(entry)));
            }
        }
        String dirRel = root.equals(dir) ? "" : root.relativize(dir).toString().replace('\\', '/');
        String dirName = dir.getFileName() == null ? "/" : dir.getFileName().toString();
        return new DirectoryNode(dirName, dirRel, children);
    }
}
