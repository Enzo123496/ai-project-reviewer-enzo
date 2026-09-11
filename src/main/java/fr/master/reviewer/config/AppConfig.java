package fr.master.reviewer.config;

import fr.master.reviewer.analysis.Criterion;

import java.util.List;

/**
 * Configuration complète, lue depuis config/reviewer.json. Aucune clé secrète ici :
 * seulement le NOM de la variable d'environnement qui la contient (apiKeyEnv).
 */
public record AppConfig(LlmSettings llm, ContextSettings context, SelectionSettings selection,
                        SandboxSettings sandbox, ReportSettings report, List<Criterion> criteria,
                        List<Profile> profiles) {

    public record LlmSettings(String active, String fallback, int maxAttempts, long initialBackoffMs,
                              boolean cacheEnabled, double temperature, int maxTokens, String outputLanguage,
                              List<ProviderSettings> providers) {
        public LlmSettings {
            maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
            initialBackoffMs = initialBackoffMs <= 0 ? 1000 : initialBackoffMs;
            maxTokens = maxTokens <= 0 ? 1500 : maxTokens;
            outputLanguage = outputLanguage == null ? "French" : outputLanguage;
            providers = providers == null ? List.of() : List.copyOf(providers);
        }
    }

    /**
     * @param type          "openai-compatible" (LM Studio, Ollama, Mistral, DeepSeek...) ou "mock"
     * @param apiKeyEnv     nom de la variable d'environnement contenant la clé (null si aucune)
     * @param responseFormat "json_object" si le serveur le supporte, sinon "none"
     */
    public record ProviderSettings(String name, String type, String baseUrl, String model, String apiKeyEnv,
                                   String responseFormat, int timeoutSeconds) {
        public ProviderSettings {
            responseFormat = responseFormat == null ? "none" : responseFormat;
            timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
        }
    }

    public record ContextSettings(int maxCharsPerChunk, int maxChunksPerCriterion, int parallelCriteria) {
        public ContextSettings {
            maxCharsPerChunk = maxCharsPerChunk <= 0 ? 12_000 : maxCharsPerChunk;
            maxChunksPerCriterion = maxChunksPerCriterion <= 0 ? 4 : maxChunksPerCriterion;
            parallelCriteria = parallelCriteria <= 0 ? 1 : parallelCriteria;
        }
    }

    public record SelectionSettings(List<String> excludeGlobs, long maxFileSizeBytes) {
        public SelectionSettings {
            excludeGlobs = excludeGlobs == null ? List.of() : List.copyOf(excludeGlobs);
            maxFileSizeBytes = maxFileSizeBytes <= 0 ? 200_000 : maxFileSizeBytes;
        }
    }

    public record SandboxSettings(String image, String memory, String cpus, int pidsLimit, int timeoutSeconds,
                                  List<String> command) {
        public SandboxSettings {
            image = image == null ? "reviewer-sandbox:latest" : image;
            memory = memory == null ? "1g" : memory;
            cpus = cpus == null ? "1.0" : cpus;
            pidsLimit = pidsLimit <= 0 ? 256 : pidsLimit;
            timeoutSeconds = timeoutSeconds <= 0 ? 300 : timeoutSeconds;
            command = command == null ? List.of() : List.copyOf(command);
        }
    }

    /** @param pdfMode "docker" (recommandé), "local" (pdflatex -no-shell-escape) ou "none" */
    public record ReportSettings(String outputDirectory, String pdfMode, String latexImage) {
        public ReportSettings {
            outputDirectory = outputDirectory == null ? "reports" : outputDirectory;
            pdfMode = pdfMode == null ? "none" : pdfMode;
            latexImage = latexImage == null ? "reviewer-latex:latest" : latexImage;
        }
    }

    public record Profile(String name, String description, List<String> criteria) {
        public Profile {
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
        }
    }
}
