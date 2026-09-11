package fr.master.reviewer.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityTest {

    @Test
    void dockerCommandAppliesLeastPrivilege() {
        List<String> cmd = new DockerCommandBuilder().build(SandboxPolicy.strict("reviewer-sandbox:latest", Duration.ofSeconds(60)),
                "reviewer-1", Path.of("/tmp/projet"), "/workspace", true, List.of("mvn", "test"));
        String line = String.join(" ", cmd);

        assertTrue(line.contains("--network none"));
        assertTrue(line.contains("--read-only"));
        assertTrue(line.contains("--user 10001:10001"));
        assertTrue(line.contains("--cap-drop ALL"));
        assertTrue(line.contains("--security-opt no-new-privileges"));
        assertTrue(line.contains("--pids-limit 256"));
        assertTrue(line.contains("--memory 1g"));
        assertTrue(line.contains("--cpus 1.0"));
        assertTrue(line.contains("--rm"));
        assertTrue(line.contains("target=/workspace,readonly"));
        assertEquals(List.of("mvn", "test"), cmd.subList(cmd.size() - 2, cmd.size()));
    }

    @Test
    void policyRefusesRootAndInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new SandboxPolicy("img", "1g", "1", 10, Duration.ofSeconds(1), false, "0:0", "64m"));
        assertThrows(IllegalArgumentException.class, () -> new SandboxPolicy("img; rm -rf /", "1g", "1", 10, Duration.ofSeconds(1), false, "1000:1000", "64m"));
    }

    @Test
    void sandboxRefusesToRunWithoutDocker() {
        ProcessRunner noDocker = (command, timeout, dir) -> new ProcessRunner.Outcome(127, "docker: not found", false);
        DockerSandbox sandbox = new DockerSandbox(noDocker, new DockerCommandBuilder(), SandboxPolicy.strict("img", Duration.ofSeconds(5)));

        assertFalse(sandbox.isAvailable());
        assertThrows(java.io.IOException.class, () -> sandbox.run(Path.of("/tmp"), List.of("mvn", "test")));
    }

    @Test
    void containerIsRemovedEvenAfterTimeout() throws Exception {
        List<List<String>> calls = new ArrayList<>();
        ProcessRunner fake = (command, timeout, dir) -> {
            calls.add(command);
            boolean isRun = command.size() > 1 && command.get(1).equals("run");
            return new ProcessRunner.Outcome(isRun ? -1 : 0, "", isRun);
        };
        DockerSandbox sandbox = new DockerSandbox(fake, new DockerCommandBuilder(), SandboxPolicy.strict("img", Duration.ofSeconds(1)));

        DockerSandbox.ExecutionResult r = sandbox.run(Path.of("/tmp"), List.of("sleep", "999"));

        assertTrue(r.timedOut());
        assertEquals(List.of("docker", "rm", "-f"), calls.get(calls.size() - 1).subList(0, 3));
    }

    @Test
    void redactorMasksSecrets() {
        SecretRedactor redactor = new SecretRedactor();
        redactor.registerSecret("ma-cle-tres-secrete");
        String out = redactor.redact("Authorization: Bearer abcdefghijkl password=\"hunter2\" key=ma-cle-tres-secrete sk-1234567890abcdefghij");

        assertFalse(out.contains("abcdefghijkl"));
        assertFalse(out.contains("hunter2"));
        assertFalse(out.contains("ma-cle-tres-secrete"));
        assertFalse(out.contains("sk-1234567890abcdefghij"));
        assertTrue(out.contains("password=***"), "le nom du secret reste visible : " + out);
    }
}
