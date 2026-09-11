package fr.master.reviewer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import fr.master.reviewer.analysis.Criterion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Charge ET valide la configuration : une erreur de config doit être détectée au démarrage, pas en pleine analyse. */
public final class ConfigLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public AppConfig load(Path path) throws ConfigException {
        if (!Files.isRegularFile(path)) {
            throw new ConfigException("Fichier de configuration introuvable : " + path.toAbsolutePath());
        }
        try {
            return validate(mapper.readValue(path.toFile(), AppConfig.class));
        } catch (UnrecognizedPropertyException e) {
            String prop = e.getPropertyName().toLowerCase(Locale.ROOT);
            if (prop.contains("key") || prop.contains("token") || prop.contains("secret") || prop.contains("password")) {
                throw new ConfigException("Propriété '" + e.getPropertyName() + "' interdite : ne mettez JAMAIS de secret "
                        + "dans la configuration. Utilisez \"apiKeyEnv\": \"NOM_DE_VARIABLE\".");
            }
            throw new ConfigException("Propriété inconnue dans la configuration : " + e.getPropertyName());
        } catch (IOException e) {
            throw new ConfigException("Configuration illisible : " + e.getMessage(), e);
        }
    }

    public AppConfig validate(AppConfig config) throws ConfigException {
        if (config.llm() == null || config.llm().providers().isEmpty()) {
            throw new ConfigException("Aucun fournisseur LLM configuré (llm.providers)");
        }
        Set<String> providerNames = new HashSet<>();
        config.llm().providers().forEach(p -> providerNames.add(p.name()));
        if (!providerNames.contains(config.llm().active())) {
            throw new ConfigException("llm.active='" + config.llm().active() + "' ne correspond à aucun fournisseur");
        }
        if (config.llm().fallback() != null && !providerNames.contains(config.llm().fallback())) {
            throw new ConfigException("llm.fallback='" + config.llm().fallback() + "' ne correspond à aucun fournisseur");
        }
        Set<String> ids = new HashSet<>();
        for (Criterion c : config.criteria()) {
            if (!ids.add(c.id())) {
                throw new ConfigException("Critère en double : " + c.id());
            }
        }
        for (AppConfig.Profile p : config.profiles()) {
            for (String id : p.criteria()) {
                if (!ids.contains(id)) {
                    throw new ConfigException("Le profil '" + p.name() + "' référence un critère inconnu : " + id);
                }
            }
        }
        return config;
    }

    public static class ConfigException extends Exception {
        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
