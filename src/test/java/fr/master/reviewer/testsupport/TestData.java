package fr.master.reviewer.testsupport;

import fr.master.reviewer.analysis.AnalysisContext;
import fr.master.reviewer.analysis.AnalysisListener;
import fr.master.reviewer.analysis.AnalysisTrace;
import fr.master.reviewer.analysis.ContentReader;
import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.analysis.LlmToolkit;
import fr.master.reviewer.analysis.MetricsCalculator;
import fr.master.reviewer.analysis.ResultAggregator;
import fr.master.reviewer.llm.parsing.LlmResultParser;
import fr.master.reviewer.llm.prompt.UntrustedContentGuard;
import fr.master.reviewer.project.DirectoryProjectLoader;
import fr.master.reviewer.project.FileClassifier;
import fr.master.reviewer.project.FileType;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.security.SecretRedactor;
import fr.master.reviewer.selection.RuleBasedSelectionStrategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fabrique de données de test : petits projets écrits dans un dossier temporaire, critères, contextes. */
public final class TestData {

    private TestData() {
    }

    public static Path writeProject(Path root, Map<String, String> files) {
        try {
            for (var e : files.entrySet()) {
                Path p = root.resolve(e.getKey());
                Files.createDirectories(p.getParent());
                Files.writeString(p, e.getValue());
            }
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Project load(Path root) {
        try {
            return new DirectoryProjectLoader(FileClassifier.defaultClassifier()).load(root.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Criterion criterion(String id, String analyzer) {
        return new Criterion(id, "Critère " + id, "description", 10, analyzer, Set.of(FileType.JAVA_SOURCE), 1);
    }

    public static LlmToolkit toolkit(int maxChars, int maxChunks) {
        return new LlmToolkit(new LlmResultParser(), new UntrustedContentGuard(() -> "nonce42"), new SecretRedactor(),
                new ResultAggregator(), "French", 0.0, 500, maxChars, maxChunks);
    }

    public static AnalysisContext context(Project project, Criterion criterion, AnalysisListener listener) {
        ContentReader reader = ContentReader.fromDisk();
        return new AnalysisContext(project, criterion, new MetricsCalculator(reader).compute(project),
                new RuleBasedSelectionStrategy(List.of("**/target/**"), 200_000), reader, listener, AnalysisTrace.noop());
    }
}
