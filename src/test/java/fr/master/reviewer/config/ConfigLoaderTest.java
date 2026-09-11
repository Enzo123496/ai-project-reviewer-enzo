package fr.master.reviewer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void loadsTheRealProjectConfiguration() throws Exception {
        AppConfig config = new ConfigLoader().load(Path.of("config/reviewer.json"));
        assertEquals("lmstudio", config.llm().active());
        assertTrue(config.criteria().size() >= 5);
    }

    @Test
    void refusesLiteralApiKeyInConfiguration(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, """
                {"llm":{"active":"x","providers":[{"name":"x","type":"mock","apiKey":"sk-abc"}]},
                 "context":{},"selection":{},"report":{},"criteria":[],"profiles":[]}
                """);
        ConfigLoader.ConfigException e = assertThrows(ConfigLoader.ConfigException.class, () -> new ConfigLoader().load(file));
        assertTrue(e.getMessage().contains("apiKeyEnv"));
    }

    @Test
    void refusesProfileReferencingUnknownCriterion(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, """
                {"llm":{"active":"x","providers":[{"name":"x","type":"mock"}]},
                 "context":{},"selection":{},"report":{},"criteria":[],"profiles":[{"name":"p","criteria":["inconnu"]}]}
                """);
        assertThrows(ConfigLoader.ConfigException.class, () -> new ConfigLoader().load(file));
    }
}
