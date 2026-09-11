package fr.master.reviewer.llm;

import fr.master.reviewer.analysis.AnalysisTrace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * PATTERN DECORATOR : cache des réponses. Relancer la même analyse sur le même code ne refait pas
 * d'appel (économie de temps/jetons et meilleure reproductibilité).
 * Clé = empreinte SHA-256 du modèle + prompts + paramètres.
 */
public final class CachingLlmProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final Map<String, LlmResponse> cache;
    private final AnalysisTrace trace;

    public CachingLlmProvider(LlmProvider delegate, Map<String, LlmResponse> cache, AnalysisTrace trace) {
        this.delegate = delegate;
        this.cache = cache;
        this.trace = trace;
    }

    @Override
    public LlmResponse ask(LlmRequest request) throws LlmException {
        String key = key(request);
        LlmResponse cached = cache.get(key);
        if (cached != null) {
            trace.cacheHit();
            return cached;
        }
        LlmResponse response = delegate.ask(request);
        cache.put(key, response);
        return response;
    }

    private String key(LlmRequest r) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = delegate.name() + '\u0000' + r.systemPrompt() + '\u0000' + r.userPrompt()
                    + '\u0000' + r.temperature() + '\u0000' + r.maxTokens();
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }
}
