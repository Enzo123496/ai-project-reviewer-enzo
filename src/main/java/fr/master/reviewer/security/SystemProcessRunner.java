package fr.master.reviewer.security;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Lance un vrai processus. La sortie est redirigée vers un fichier temporaire (évite le blocage si le
 * tampon se remplit) et seule la FIN (64 Ko max) est relue : un projet bavard ne sature pas la mémoire.
 * Les arguments sont passés en liste, sans shell : pas d'injection de commande possible.
 */
public final class SystemProcessRunner implements ProcessRunner {

    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    @Override
    public Outcome run(List<String> command, Duration timeout, Path workingDirectory) throws IOException, InterruptedException {
        Path log = Files.createTempFile("reviewer-process-", ".log");
        try {
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
            if (workingDirectory != null) {
                pb.directory(workingDirectory.toFile());
            }
            Process process = pb.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            return new Outcome(finished ? process.exitValue() : -1, tail(log), !finished);
        } finally {
            Files.deleteIfExists(log);
        }
    }

    private static String tail(Path file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            long start = Math.max(0, length - MAX_OUTPUT_BYTES);
            byte[] bytes = new byte[(int) (length - start)];
            raf.seek(start);
            raf.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return start > 0 ? "[... sortie tronquée ...]\n" + text : text;
        }
    }
}
