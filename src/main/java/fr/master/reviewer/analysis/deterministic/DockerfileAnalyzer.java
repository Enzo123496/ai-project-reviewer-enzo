package fr.master.reviewer.analysis.deterministic;

import fr.master.reviewer.analysis.AnalysisContext;
import fr.master.reviewer.analysis.Analyzer;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.project.FileNode;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Analyse déterministe des bonnes pratiques Docker (lecture du Dockerfile, jamais de docker build). */
public final class DockerfileAnalyzer implements Analyzer {

    private static final Pattern SECRET_ENV = Pattern.compile("(?im)^\\s*(ENV|ARG)\\s+\\S*(password|passwd|secret|token|api_?key)\\S*[ =]");

    @Override
    public CriterionResult analyze(AnalysisContext ctx) {
        List<FileNode> dockerfiles = ctx.project().files()
                .filter(f -> f.name().toLowerCase(Locale.ROOT).startsWith("dockerfile")).toList();
        if (dockerfiles.isEmpty()) {
            return new Checklist()
                    .check(false, 10, null, "Aucun Dockerfile", "Fournir un Dockerfile pour un déploiement reproductible")
                    .toResult(ctx.criterion(), "déterministe (Dockerfile)");
        }
        FileNode main = dockerfiles.get(0);
        String content = ctx.reader().read(main);
        String lower = content.toLowerCase(Locale.ROOT);
        List<String> fromLines = lower.lines().map(String::strip).filter(l -> l.startsWith("from ")).toList();
        boolean pinned = !fromLines.isEmpty() && fromLines.stream().allMatch(l -> {
            String image = l.split("\\s+")[1];
            return image.contains(":") && !image.endsWith(":latest") || image.contains("@sha256:");
        });
        boolean nonRoot = lower.lines().map(String::strip).anyMatch(l -> l.startsWith("user ") && !l.matches("user\\s+(root|0)(:.*)?"));
        boolean multiStage = fromLines.size() > 1;
        boolean healthcheck = lower.contains("healthcheck");
        boolean dockerignore = ctx.project().files().anyMatch(f -> f.name().equals(".dockerignore"));
        boolean secrets = SECRET_ENV.matcher(content).find();
        boolean addUrl = lower.lines().anyMatch(l -> l.strip().matches("add\\s+https?://.*"));

        Checklist checklist = new Checklist()
                .check(pinned, 2, "Image de base avec version explicite", "Image de base sans version précise (ou :latest)",
                        "Épingler la version de l'image (ex. eclipse-temurin:21-jre)")
                .check(nonRoot, 2, "Exécution avec un utilisateur non-root", "Le conteneur s'exécute en root",
                        "Ajouter un utilisateur dédié et l'instruction USER")
                .check(multiStage, 1, "Build multi-étapes (image finale plus légère)", "Build en une seule étape",
                        "Séparer compilation et exécution (multi-stage build)")
                .check(healthcheck, 1, "HEALTHCHECK défini", "Pas de HEALTHCHECK", "Ajouter un HEALTHCHECK si pertinent")
                .check(dockerignore, 1, ".dockerignore présent", "Pas de .dockerignore",
                        "Ajouter un .dockerignore (target/, .git/, secrets)")
                .check(!secrets, 2, "Aucun secret apparent dans ENV/ARG", "Secret probable dans une instruction ENV/ARG",
                        "Passer les secrets à l'exécution (variables d'environnement, secrets Docker)")
                .check(!addUrl, 1, "Pas de ADD d'URL distante", "ADD d'une URL distante",
                        "Préférer COPY et un téléchargement vérifié");
        if (secrets) {
            checklist.issue("Secret potentiellement exposé dans " + main.relativePath());
        }
        return checklist.toResult(ctx.criterion(), "déterministe (" + main.relativePath() + ")");
    }
}
