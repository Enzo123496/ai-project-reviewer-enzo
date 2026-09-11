package fr.master.reviewer.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Charge un projet depuis une archive .zip, extraite dans un dossier de travail.
 * Protections : "Zip Slip" (entrée ../../ qui sortirait du dossier), "zip bomb" (taille/nombre bornés).
 */
public final class ZipProjectLoader implements ProjectLoader {

    private final DirectoryProjectLoader directoryLoader;
    private final Path workDirectory;
    private final long maxUncompressedBytes;
    private final int maxEntries;

    public ZipProjectLoader(DirectoryProjectLoader directoryLoader, Path workDirectory) {
        this(directoryLoader, workDirectory, 300L * 1024 * 1024, 50_000);
    }

    public ZipProjectLoader(DirectoryProjectLoader directoryLoader, Path workDirectory,
                            long maxUncompressedBytes, int maxEntries) {
        this.directoryLoader = directoryLoader;
        this.workDirectory = workDirectory;
        this.maxUncompressedBytes = maxUncompressedBytes;
        this.maxEntries = maxEntries;
    }

    @Override
    public boolean supports(String source) {
        return source.toLowerCase(Locale.ROOT).endsWith(".zip") && Files.isRegularFile(Path.of(source));
    }

    @Override
    public Project load(String source) throws ProjectLoadException {
        Path zip = Path.of(source);
        String name = zip.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
        try {
            Files.createDirectories(workDirectory);
            Path destination = Files.createTempDirectory(workDirectory, "import-").toRealPath();
            extract(zip, destination);
            return directoryLoader.load(destination, name, source);
        } catch (IOException e) {
            throw new ProjectLoadException("Archive illisible : " + e.getMessage(), e);
        }
    }

    void extract(Path zip, Path destination) throws IOException, ProjectLoadException {
        long total = 0;
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entries > maxEntries) {
                    throw new ProjectLoadException("Archive refusée : trop d'entrées");
                }
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) {
                    throw new ProjectLoadException("Archive refusée : chemin dangereux (Zip Slip) -> " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                total += copyBounded(in, target, maxUncompressedBytes - total);
            }
        }
    }

    private static long copyBounded(InputStream in, Path target, long remaining) throws IOException, ProjectLoadException {
        byte[] buffer = new byte[8192];
        long written = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                written += n;
                if (written > remaining) {
                    throw new ProjectLoadException("Archive refusée : taille décompressée trop grande (zip bomb ?)");
                }
                out.write(buffer, 0, n);
            }
        }
        return written;
    }
}
