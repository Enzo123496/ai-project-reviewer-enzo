package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.LlmProvider;
import fr.master.reviewer.project.FileNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analyse par LLM sur le CODE lui-même (lisibilité, exceptions, SOLID, sécurité...).
 * Spécialise uniquement l'étape "préparer les segments" du Template Method :
 * 1. on tente l'ordre naturel (fichiers d'un même dossier regroupés, meilleur contexte pour le modèle) ;
 * 2. si tout ne tient pas, on répartit les fichiers dossier par dossier pour un échantillon représentatif ;
 * 3. on indique la COUVERTURE réelle dans les avertissements (transparence du rapport).
 */
public final class CodeChunkLlmAnalyzer extends AbstractLlmAnalyzer {

    public CodeChunkLlmAnalyzer(LlmProvider llm, LlmToolkit toolkit) {
        super(llm, toolkit);
    }

    @Override
    protected SegmentPlan prepareSegments(AnalysisContext ctx, List<FileNode> files) {
        Map<String, String> readCache = new HashMap<>();
        ContentReader cachedReader = f -> readCache.computeIfAbsent(f.relativePath(), k -> ctx.reader().read(f));
        Chunker chunker = new Chunker(toolkit.maxCharsPerChunk(), toolkit.maxChunks());

        Chunker.ChunkPlan plan = chunker.plan(files, cachedReader);
        boolean interleaved = false;
        if (plan.truncated()) {
            plan = chunker.plan(FileInterleaver.interleaveByDirectory(files), cachedReader);
            interleaved = true;
        }

        List<Segment> segments = plan.chunks().stream()
                .map(c -> new Segment(label(c.files()), c.content()))
                .toList();
        List<String> warnings = new ArrayList<>();
        if (plan.truncated()) {
            warnings.add(coverage(plan, files, interleaved));
        }
        return new SegmentPlan(segments, warnings);
    }

    private String coverage(Chunker.ChunkPlan plan, List<FileNode> files, boolean interleaved) {
        long sent = plan.sentFileCount();
        long whole = sent - plan.partiallySentFiles().size();
        Set<String> sentPaths = plan.chunks().stream().flatMap(c -> c.files().stream()).collect(Collectors.toSet());
        long directories = files.stream().map(FileInterleaver::directoryOf).distinct().count();
        long coveredDirectories = files.stream().filter(f -> sentPaths.contains(f.relativePath()))
                .map(FileInterleaver::directoryOf).distinct().count();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Couverture partielle (limite de %d segments) : %d/%d fichiers envoyés en entier (%d %%)",
                toolkit.maxChunks(), whole, files.size(), Math.round(100.0 * whole / files.size())));
        if (!plan.partiallySentFiles().isEmpty()) {
            sb.append(", ").append(plan.partiallySentFiles().size()).append(" en partie");
        }
        sb.append(", ").append(coveredDirectories).append('/').append(directories).append(" dossier(s) représenté(s)");
        if (interleaved) {
            sb.append(" ; fichiers répartis dossier par dossier pour un échantillon représentatif");
        }
        sb.append(". Non envoyés : ").append(String.join(", ", plan.skippedFiles().stream().limit(6).toList()));
        if (plan.skippedFiles().size() > 6) {
            sb.append("... (+").append(plan.skippedFiles().size() - 6).append(')');
        }
        return sb.toString();
    }

    private static String label(List<String> files) {
        String shown = String.join(", ", files.stream().limit(5).toList());
        return "source files: " + shown + (files.size() > 5 ? " (+" + (files.size() - 5) + " more)" : "");
    }
}
