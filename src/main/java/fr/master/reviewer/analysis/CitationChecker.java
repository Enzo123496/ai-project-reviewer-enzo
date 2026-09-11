package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.Project;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DÉTECTION D'HALLUCINATIONS : un LLM peut citer un fichier qui n'existe pas ("voir UserService.java").
 * Le validateur de la Team 1 vérifie la FORME de la réponse ; ici on vérifie un point de FOND vérifiable
 * automatiquement : chaque nom de fichier cité doit exister dans le projet analysé.
 */
public final class CitationChecker {

    private static final Pattern FILE_REFERENCE = Pattern.compile(
            "(?i)(?<![\\w.])[\\w./-]*[\\w-]+\\.(java|kt|xml|gradle|kts|md|ya?ml|properties|json|sh|toml)\\b");
    private static final int MAX_REPORTED = 5;

    public List<String> unknownCitations(Project project, Collection<String> texts) {
        Set<String> paths = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (FileNode f : project.files().toList()) {
            paths.add(f.relativePath().toLowerCase(Locale.ROOT));
            names.add(f.name().toLowerCase(Locale.ROOT));
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (String text : texts) {
            Matcher m = FILE_REFERENCE.matcher(text);
            while (m.find() && unknown.size() < MAX_REPORTED) {
                String reference = m.group().replaceFirst("^\\./", "");
                String lower = reference.toLowerCase(Locale.ROOT);
                String fileName = lower.substring(lower.lastIndexOf('/') + 1);
                boolean known = lower.contains("/")
                        ? paths.stream().anyMatch(p -> p.equals(lower) || p.endsWith("/" + lower))
                        : names.contains(fileName);
                if (!known) {
                    unknown.add(reference);
                }
            }
        }
        return List.copyOf(unknown);
    }
}
