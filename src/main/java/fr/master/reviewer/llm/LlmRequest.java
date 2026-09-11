package fr.master.reviewer.llm;

import java.util.Map;

/**
 * Requête indépendante de tout fournisseur. Les instructions (systemPrompt) sont séparées des données
 * (userPrompt) : c'est la première barrière contre l'injection de prompt.
 *
 * @param metadata informations de traçabilité (purpose, criterionId, maxScore...) jamais envoyées au modèle
 */
public record LlmRequest(String systemPrompt, String userPrompt, double temperature, int maxTokens,
                         Map<String, String> metadata) {

    public LlmRequest {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        userPrompt = userPrompt == null ? "" : userPrompt;
        maxTokens = maxTokens <= 0 ? 1500 : maxTokens;
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    public String meta(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public LlmRequest withUserPrompt(String newUserPrompt, String purpose) {
        java.util.Map<String, String> m = new java.util.HashMap<>(metadata);
        m.put("purpose", purpose);
        return new LlmRequest(systemPrompt, newUserPrompt, temperature, maxTokens, m);
    }
}
