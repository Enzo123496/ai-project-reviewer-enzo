package fr.master.reviewer.security;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Abstraction du lancement de processus : les tests vérifient les commandes Docker SANS Docker. */
public interface ProcessRunner {

    record Outcome(int exitCode, String output, boolean timedOut) {
    }

    Outcome run(List<String> command, Duration timeout, Path workingDirectory) throws IOException, InterruptedException;
}
