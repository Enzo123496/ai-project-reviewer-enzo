package fr.master.reviewer.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipProjectLoaderTest {

    private static Path zip(Path dir, String entryName, String content) throws Exception {
        Path zip = dir.resolve("projet.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(content.getBytes());
            out.closeEntry();
        }
        return zip;
    }

    @Test
    void extractsAndLoadsArchive(@TempDir Path dir) throws Exception {
        Path zip = zip(dir, "src/main/java/App.java", "class App {}");
        ZipProjectLoader loader = new ZipProjectLoader(new DirectoryProjectLoader(FileClassifier.defaultClassifier()), dir.resolve("work"));

        assertTrue(loader.supports(zip.toString()));
        Project project = loader.load(zip.toString());

        assertEquals("projet", project.name());
        assertEquals(1, project.files().count());
    }

    @Test
    void rejectsZipSlip(@TempDir Path dir) throws Exception {
        Path zip = zip(dir, "../../evil.sh", "rm -rf ~");
        ZipProjectLoader loader = new ZipProjectLoader(new DirectoryProjectLoader(FileClassifier.defaultClassifier()), dir.resolve("work"));

        ProjectLoadException e = assertThrows(ProjectLoadException.class, () -> loader.load(zip.toString()));
        assertTrue(e.getMessage().contains("Zip Slip"));
        assertFalse(Files.exists(dir.resolve("evil.sh")));
    }

    @Test
    void rejectsZipBomb(@TempDir Path dir) throws Exception {
        Path zip = dir.resolve("bomb.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("big.txt"));
            OutputStream o = out;
            o.write(new byte[5000]);
            out.closeEntry();
        }
        ZipProjectLoader loader = new ZipProjectLoader(new DirectoryProjectLoader(FileClassifier.defaultClassifier()),
                dir.resolve("work"), 1000, 10);
        assertThrows(ProjectLoadException.class, () -> loader.load(zip.toString()));
    }
}
