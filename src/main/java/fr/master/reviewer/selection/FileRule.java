package fr.master.reviewer.selection;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Set;

/** Règle d'inclusion/exclusion de fichiers, combinable (and / or / negate). */
@FunctionalInterface
public interface FileRule {

    boolean matches(FileNode file);

    default FileRule and(FileRule other) {
        return f -> matches(f) && other.matches(f);
    }

    default FileRule or(FileRule other) {
        return f -> matches(f) || other.matches(f);
    }

    default FileRule negate() {
        return f -> !matches(f);
    }

    static FileRule ofTypes(Set<FileType> types) {
        return f -> types.isEmpty() || types.contains(f.type());
    }

    /** Glob sur le chemin relatif, ex. "**&#47;target/**". On préfixe par "/" pour que ** couvre la racine. */
    static FileRule glob(String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        return f -> matcher.matches(Path.of("/" + f.relativePath()));
    }

    static FileRule maxSize(long bytes) {
        return f -> f.size() <= bytes;
    }
}
