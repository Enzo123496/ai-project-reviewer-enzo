package fr.master.reviewer.llm;

import fr.master.reviewer.analysis.AnalysisTrace;
import fr.master.reviewer.config.AppConfig.LlmSettings;
import fr.master.reviewer.config.AppConfig.ProviderSettings;
import fr.master.reviewer.security.SecretRedactor;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * FACTORY : construit, à partir de la configuration, un LlmProvider "prêt à l'emploi" en empilant
 * les décorateurs dans le bon ordre :
 *
 *   Caching( Fallback( Retrying( Tracing(principal) ), Retrying( Tracing(repli) ) ) )
 *
 * Le reste de l'application ne voit qu'un LlmProvider et ignore tout de cet empilement.
 */
public final class LlmProviderFactory {

    /** Fonction de création d'un fournisseur concret pour un "type" de configuration. */
    @FunctionalInterface
    public interface ProviderCreator {
        LlmProvider create(ProviderSettings settings, String apiKey);
    }

    private final LlmSettings settings;
    private final Function<String, String> environment;
    private final SecretRedactor redactor;
    private final RetryingLlmProvider.Sleeper sleeper;
    private final Map<String, ProviderCreator> creators = new HashMap<>();
    private final Map<String, LlmResponse> sharedCache = new ConcurrentHashMap<>();

    public LlmProviderFactory(LlmSettings settings, Function<String, String> environment, SecretRedactor redactor,
                              RetryingLlmProvider.Sleeper sleeper) {
        this.settings = settings;
        this.environment = environment;
        this.redactor = redactor;
        this.sleeper = sleeper;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        registerType("openai-compatible", (s, key) -> new OpenAiCompatibleProvider(s, http, key));
        registerType("mock", (s, key) -> new MockLlmProvider());
    }

    /** Point d'extension : nouveau format d'API = nouvel adaptateur enregistré ici (ou depuis AppFactory). */
    public void registerType(String type, ProviderCreator creator) {
        creators.put(type, creator);
    }

    public List<String> providerNames() {
        return settings.providers().stream().map(ProviderSettings::name).toList();
    }

    public String activeName() {
        return settings.active();
    }

    public LlmProvider create(String providerName, AnalysisTrace trace) throws LlmException {
        String name = providerName == null ? settings.active() : providerName;
        List<LlmProvider> chain = new ArrayList<>();
        chain.add(resilient(name, trace));
        if (settings.fallback() != null && !settings.fallback().equals(name)) {
            chain.add(resilient(settings.fallback(), trace));
        }
        LlmProvider provider = chain.size() == 1 ? chain.get(0) : new FallbackLlmProvider(chain, trace);
        return settings.cacheEnabled() ? new CachingLlmProvider(provider, sharedCache, trace) : provider;
    }

    private LlmProvider resilient(String name, AnalysisTrace trace) throws LlmException {
        ProviderSettings ps = settings.providers().stream().filter(p -> p.name().equals(name)).findFirst()
                .orElseThrow(() -> new LlmException(LlmException.Kind.CONFIGURATION, "fournisseur inconnu : " + name));
        ProviderCreator creator = creators.get(ps.type());
        if (creator == null) {
            throw new LlmException(LlmException.Kind.CONFIGURATION, "type de fournisseur non supporté : " + ps.type());
        }
        LlmProvider concrete = creator.create(ps, resolveApiKey(ps));
        return new RetryingLlmProvider(new TracingLlmProvider(concrete, trace), settings.maxAttempts(),
                settings.initialBackoffMs(), trace, sleeper);
    }

    /** La clé est lue dans l'ENVIRONNEMENT, jamais dans un fichier du dépôt, puis enregistrée pour être masquée. */
    private String resolveApiKey(ProviderSettings ps) throws LlmException {
        if (ps.apiKeyEnv() == null || ps.apiKeyEnv().isBlank()) {
            return null;
        }
        String key = environment.apply(ps.apiKeyEnv());
        if (key == null || key.isBlank()) {
            throw new LlmException(LlmException.Kind.CONFIGURATION, "la variable d'environnement "
                    + ps.apiKeyEnv() + " n'est pas définie (nécessaire pour " + ps.name() + ")");
        }
        redactor.registerSecret(key);
        return key;
    }
}
