package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;
import fr.master.reviewer.project.Project;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calcule les ProjectMetrics par simple lecture de texte (aucune exécution du projet). */
public final class MetricsCalculator {

    private static final Pattern EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*}");
    private static final Pattern GENERIC_CATCH = Pattern.compile("catch\\s*\\(\\s*(final\\s+)?(Exception|Throwable)\\s+\\w+\\s*\\)");
    private static final Pattern TODO = Pattern.compile("\\b(TODO|FIXME)\\b");

    private final ContentReader reader;

    public MetricsCalculator(ContentReader reader) {
        this.reader = reader;
    }

    public ProjectMetrics compute(Project project) {
        List<FileNode> files = project.files().toList();
        int javaFiles = 0;
        int testFiles = 0;
        long javaLines = 0;
        String largest = "-";
        int largestLines = 0;
        int emptyCatches = 0;
        int genericCatches = 0;
        int todos = 0;
        for (FileNode f : files) {
            if (f.type() != FileType.JAVA_SOURCE && f.type() != FileType.TEST) {
                continue;
            }
            String content = reader.read(f);
            int lines = content.isEmpty() ? 0 : (int) content.lines().count();
            if (f.type() == FileType.TEST) {
                testFiles++;
            } else {
                javaFiles++;
                javaLines += lines;
                if (lines > largestLines) {
                    largestLines = lines;
                    largest = f.relativePath();
                }
            }
            emptyCatches += count(EMPTY_CATCH, content);
            genericCatches += count(GENERIC_CATCH, content);
            todos += count(TODO, content);
        }
        boolean readme = files.stream().anyMatch(f -> f.name().toLowerCase().startsWith("readme"));
        boolean docker = files.stream().anyMatch(f -> f.name().toLowerCase().startsWith("dockerfile"));
        boolean build = files.stream().anyMatch(f -> f.type() == FileType.BUILD);
        return new ProjectMetrics(files.size(), javaFiles, testFiles, javaLines, largest, largestLines,
                emptyCatches, genericCatches, todos, readme, docker, build);
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
