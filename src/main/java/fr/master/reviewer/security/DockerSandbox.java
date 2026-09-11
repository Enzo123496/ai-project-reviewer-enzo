package fr.master.reviewer.security;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Exécute une commande DANS un conteneur Docker durci, jamais sur l'hôte.
 * Architecture recommandée : Machine virtuelle -> Docker -> projet évalué.
 * Si Docker est absent, l'exécution est REFUSÉE (pas de repli dangereux vers une exécution locale).
 */
public final class DockerSandbox {

    public record ExecutionResult(int exitCode, String output, boolean timedOut, Duration duration) {
    }

    private final ProcessRunner runner;
    private final DockerCommandBuilder builder;
    private final SandboxPolicy policy;
    private Boolean available;

    public DockerSandbox(ProcessRunner runner, DockerCommandBuilder builder, SandboxPolicy policy) {
        this.runner = runner;
        this.builder = builder;
        this.policy = policy;
    }

    public synchronized boolean isAvailable() {
        if (available == null) {
            try {
                available = runner.run(List.of("docker", "version", "--format", "{{.Server.Version}}"),
                        Duration.ofSeconds(10), null).exitCode() == 0;
            } catch (IOException e) {
                available = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                available = false;
            }
        }
        return available;
    }

    /** Le projet est monté en LECTURE SEULE ; la commande doit copier les sources dans /tmp pour compiler. */
    public ExecutionResult run(Path projectDirectory, List<String> command) throws IOException, InterruptedException {
        if (!isAvailable()) {
            throw new IOException("Docker indisponible : exécution refusée (jamais d'exécution directe sur l'hôte)");
        }
        String name = "reviewer-" + UUID.randomUUID();
        List<String> cmd = builder.build(policy, name, projectDirectory, "/workspace", true, command);
        long start = System.currentTimeMillis();
        try {
            ProcessRunner.Outcome outcome = runner.run(cmd, policy.timeout(), null);
            return new ExecutionResult(outcome.exitCode(), outcome.output(), outcome.timedOut(),
                    Duration.ofMillis(System.currentTimeMillis() - start));
        } finally {
            // Tuer le client docker ne tue pas le conteneur : on le supprime explicitement (sans effet s'il n'existe plus).
            runner.run(List.of("docker", "rm", "-f", name), Duration.ofSeconds(30), null);
        }
    }

    public SandboxPolicy policy() {
        return policy;
    }
}
