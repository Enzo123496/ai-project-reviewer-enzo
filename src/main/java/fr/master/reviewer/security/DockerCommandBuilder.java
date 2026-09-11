package fr.master.reviewer.security;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Construit la ligne de commande "docker run" durcie. Isolée dans sa propre classe pour pouvoir
 * vérifier par des tests unitaires que CHAQUE restriction est bien présente.
 */
public final class DockerCommandBuilder {

    public List<String> build(SandboxPolicy policy, String containerName, Path hostDirectory, String containerDirectory,
                              boolean readOnlyMount, List<String> command) {
        String source = hostDirectory.toAbsolutePath().normalize().toString();
        if (source.contains(",")) {
            throw new IllegalArgumentException("Chemin refusé (virgule interdite dans un montage) : " + source);
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Commande vide");
        }
        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm", "--name", containerName));
        if (!policy.networkEnabled()) {
            cmd.addAll(List.of("--network", "none"));
        }
        cmd.addAll(List.of(
                "--memory", policy.memory(), "--memory-swap", policy.memory(),
                "--cpus", policy.cpus(),
                "--pids-limit", String.valueOf(policy.pidsLimit()),
                "--read-only",
                "--tmpfs", "/tmp:rw,nosuid,nodev,size=" + policy.tmpfsSize(),
                "--user", policy.user(),
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--ulimit", "nofile=1024:1024",
                "-e", "HOME=/tmp",
                "--mount", "type=bind,source=" + source + ",target=" + containerDirectory + (readOnlyMount ? ",readonly" : ""),
                "-w", containerDirectory,
                policy.image()));
        cmd.addAll(command);
        return cmd;
    }
}
