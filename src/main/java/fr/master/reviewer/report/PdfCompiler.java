package fr.master.reviewer.report;

import fr.master.reviewer.security.DockerCommandBuilder;
import fr.master.reviewer.security.ProcessRunner;
import fr.master.reviewer.security.SandboxPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compilation .tex -> .pdf, optionnelle et DURCIE :
 * - "-no-shell-escape" : LaTeX ne peut lancer aucune commande système (\write18 désactivé) ;
 * - mode "docker" : pdflatex tourne dans un conteneur sans réseau, non-root, avec limites ;
 * - le contenu est de toute façon échappé (LatexEscaper) : défense en profondeur.
 */
public final class PdfCompiler {

    public enum Mode { NONE, LOCAL, DOCKER }

    private final Mode mode;
    private final String latexImage;
    private final ProcessRunner runner;
    private final DockerCommandBuilder dockerBuilder;

    public PdfCompiler(Mode mode, String latexImage, ProcessRunner runner, DockerCommandBuilder dockerBuilder) {
        this.mode = mode;
        this.latexImage = latexImage;
        this.runner = runner;
        this.dockerBuilder = dockerBuilder;
    }

    public Mode mode() {
        return mode;
    }

    public Optional<Path> compile(Path texFile) throws IOException {
        if (mode == Mode.NONE) {
            return Optional.empty();
        }
        Path dir = texFile.toAbsolutePath().getParent();
        String name = texFile.getFileName().toString();
        List<String> latex = List.of("pdflatex", "-no-shell-escape", "-interaction=nonstopmode", "-halt-on-error", name);
        List<String> command;
        if (mode == Mode.DOCKER) {
            String user = unixOwner(dir);
            SandboxPolicy policy = new SandboxPolicy(latexImage, "512m", "1.0", 64, Duration.ofSeconds(90), false, user, "64m");
            command = dockerBuilder.build(policy, "reviewer-latex-" + UUID.randomUUID(), dir, "/work", false, latex);
        } else {
            command = latex;
        }
        try {
            // Deux passes : la table des matières et les longtables ont besoin d'une seconde compilation.
            for (int pass = 0; pass < 2; pass++) {
                ProcessRunner.Outcome outcome = runner.run(command, Duration.ofSeconds(90), dir);
                if (outcome.timedOut() || outcome.exitCode() != 0) {
                    throw new IOException("Échec de pdflatex (code " + outcome.exitCode() + ") : "
                            + lastLines(outcome.output()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Compilation interrompue", e);
        }
        Path pdf = dir.resolve(name.replaceFirst("\\.tex$", ".pdf"));
        return Files.exists(pdf) ? Optional.of(pdf) : Optional.empty();
    }

    /** Le conteneur écrit le PDF avec l'uid du propriétaire du dossier (jamais root). */
    private static String unixOwner(Path dir) {
        try {
            Object uid = Files.getAttribute(dir, "unix:uid");
            Object gid = Files.getAttribute(dir, "unix:gid");
            if (!"0".equals(String.valueOf(uid))) {
                return uid + ":" + gid;
            }
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            // Windows : attribut indisponible
        }
        return "10001:10001";
    }

    private static String lastLines(String output) {
        List<String> lines = output.lines().toList();
        return String.join(" | ", lines.subList(Math.max(0, lines.size() - 5), lines.size()));
    }
}
