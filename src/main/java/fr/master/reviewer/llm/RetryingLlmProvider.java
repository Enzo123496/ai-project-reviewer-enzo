package fr.master.reviewer.llm;

import fr.master.reviewer.analysis.AnalysisTrace;

/**
 * PATTERN DECORATOR : réessaie les pannes TEMPORAIRES (timeout, serveur indisponible, HTTP 5xx/429,
 * réponse vide) avec un délai qui double à chaque tentative (backoff exponentiel), dans la limite de
 * maxAttempts. Les erreurs définitives (clé invalide...) sont relancées immédiatement.
 */
public final class RetryingLlmProvider implements LlmProvider {

    /** Abstraction de Thread.sleep : les tests injectent un faux "sleeper" et s'exécutent instantanément. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final LlmProvider delegate;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final AnalysisTrace trace;
    private final Sleeper sleeper;

    public RetryingLlmProvider(LlmProvider delegate, int maxAttempts, long initialBackoffMs,
                               AnalysisTrace trace, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts doit être >= 1");
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.trace = trace;
        this.sleeper = sleeper;
    }

    @Override
    public LlmResponse ask(LlmRequest request) throws LlmException {
        long backoff = initialBackoffMs;
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.ask(request);
            } catch (LlmException e) {
                if (!e.isRetryable() || attempt >= maxAttempts) {
                    throw e;
                }
                trace.retry(attempt + 1, e.getMessage());
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException(LlmException.Kind.INTERRUPTED, "attente interrompue", false, ie);
                }
                backoff = Math.min(backoff * 2, 30_000);
            }
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }
}
