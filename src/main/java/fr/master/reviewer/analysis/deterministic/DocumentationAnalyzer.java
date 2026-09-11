package fr.master.reviewer.analysis.deterministic;

import fr.master.reviewer.analysis.AnalysisContext;
import fr.master.reviewer.analysis.Analyzer;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Analyse déterministe de la documentation : README (présence, longueur, sections utiles) et Javadoc. */
public final class DocumentationAnalyzer implements Analyzer {

    private static final Pattern PUBLIC_TYPE = Pattern.compile("(?m)^\\s*public\\s+(?:abstract\\s+|final\\s+|sealed\\s+)*(class|interface|enum|record)\\s");
    private static final Pattern DOCUMENTED_TYPE = Pattern.compile("\\*/\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*public\\s+(?:abstract\\s+|final\\s+|sealed\\s+)*(class|interface|enum|record)\\s");

    @Override
    public CriterionResult analyze(AnalysisContext ctx) {
        List<FileNode> files = ctx.project().files().toList();
        Optional<FileNode> readme = files.stream()
                .filter(f -> f.relativePath().toLowerCase(Locale.ROOT).matches("readme(\\.\\w+)?")).findFirst();
        String readmeText = readme.map(ctx.reader()::read).orElse("").toLowerCase(Locale.ROOT);
        boolean install = readmeText.matches("(?s).*(install|compil|build|mvn|gradle).*");
        boolean usage = readmeText.matches("(?s).*(usage|utilisation|lancer|run|exécut|execut|démarr).*");

        int publicTypes = 0;
        int documented = 0;
        for (FileNode f : files) {
            if (f.type() == FileType.JAVA_SOURCE) {
                String content = ctx.reader().read(f);
                publicTypes += count(PUBLIC_TYPE, content);
                documented += count(DOCUMENTED_TYPE, content);
            }
        }
        double javadocRatio = publicTypes == 0 ? 0 : (double) documented / publicTypes;
        boolean docsFolder = files.stream().anyMatch(f -> f.relativePath().startsWith("docs/"));

        return new Checklist()
                .check(readme.isPresent(), 3, "README présent", "README absent à la racine du projet",
                        "Ajouter un README expliquant le projet")
                .check(readmeText.length() > 1500, 1, "README détaillé", "README très court",
                        "Détailler le README (objectif, architecture, captures)")
                .check(install, 1, "Instructions de compilation/installation", "Pas d'instructions de compilation",
                        "Documenter la compilation (mvn package...)")
                .check(usage, 1, "Instructions d'utilisation", "Pas d'instructions d'utilisation",
                        "Documenter le lancement et un exemple d'utilisation")
                .partial(javadocRatio / 0.6, 3, String.format("%d/%d types publics documentés (Javadoc)", documented, publicTypes),
                        String.format("Javadoc rare : %d/%d types publics documentés", documented, publicTypes),
                        "Documenter au minimum les interfaces et classes publiques")
                .check(docsFolder, 1, "Dossier docs/ présent", null, null)
                .toResult(ctx.criterion(), "déterministe (README + Javadoc)");
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
