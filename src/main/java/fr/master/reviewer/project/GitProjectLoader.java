package fr.master.reviewer.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * (Optionnel) Clone un dépôt Git public en HTTPS, sans historique et SANS hooks.
 * Cloner n'exécute pas le code du projet ; on désactive tout de même les hooks par prudence.
 */
public final class GitProjectLoader implements ProjectLoader {

    private final DirectoryProjectLoader directoryLoader;
    private final Path workDirectory;

    public GitProjectLoader(DirectoryProjectLoader directoryLoader, Path workDirectory) {
        this.directoryLoader = directoryLoader;
        this.workDirectory = workDirectory;
    }

    @Override
    public boolean supports(String source) {
        return source.startsWith("https://");
    }

    @Override
    public Project load(String source) throws ProjectLoadException {
        if (!source.matches("https://[\\w.-]+/[\\w./-]+")) {
            throw new ProjectLoadException("URL Git refusée (seul https://hote/chemin est accepté)");
        }
        try {
            Files.createDirectories(workDirectory);
            Path destination = Files.createTempDirectory(workDirectory, "git-");
            ProcessBuilder pb = new ProcessBuilder(List.of("git", "-c", "core.hooksPath=/dev/null",
                    "-c", "protocol.file.allow=never", "clone", "--depth", "1", "--no-tags", "--", source,
                    destination.toString()));
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ProjectLoadException("Clonage trop long, abandonné");
            }
            if (process.exitValue() != 0) {
                throw new ProjectLoadException("Échec du clonage (code " + process.exitValue() + ")");
            }
            String name = source.replaceFirst("\\.git$", "").replaceFirst(".*/", "");
            return directoryLoader.load(destination, name, source);
        } catch (IOException e) {
            throw new ProjectLoadException("git indisponible : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProjectLoadException("Clonage interrompu", e);
        }
    }
}
