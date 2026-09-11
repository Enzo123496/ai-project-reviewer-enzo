package fr.master.reviewer.analysis;

import fr.master.reviewer.security.SecretRedactor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Journal de traçabilité d'UNE analyse (thread-safe). Chaque message passe par le SecretRedactor :
 * une clé d'API ou un mot de passe ne peut pas finir dans l'historique ou dans le rapport.
 */
public final class AnalysisTrace {

    private static final Logger LOG = Logger.getLogger(AnalysisTrace.class.getName());
    private static final int MAX_EVENTS = 2000;

    private final SecretRedactor redactor;
    private final Instant start = Instant.now();
    private final List<String> events = new ArrayList<>();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private final AtomicInteger llmFailures = new AtomicInteger();
    private final AtomicInteger retries = new AtomicInteger();
    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();

    public AnalysisTrace(SecretRedactor redactor) {
        this.redactor = redactor;
    }

    public static AnalysisTrace noop() {
        return new AnalysisTrace(new SecretRedactor());
    }

    public void info(String message) {
        record("INFO", message);
    }

    public void error(String message) {
        record("ERREUR", message);
    }

    private void record(String level, String message) {
        String clean = redactor.redact(message);
        String line = Instant.now().truncatedTo(ChronoUnit.MILLIS) + " [" + level + "] " + clean;
        synchronized (events) {
            if (events.size() < MAX_EVENTS) {
                events.add(line);
            }
        }
        LOG.log("ERREUR".equals(level) ? Level.WARNING : Level.INFO, clean);
    }

    public void llmCall(long durationMs, long prompt, long completion, String model) {
        llmCalls.incrementAndGet();
        promptTokens.addAndGet(Math.max(prompt, 0));
        completionTokens.addAndGet(Math.max(completion, 0));
        info("Appel LLM réussi (" + model + ") en " + durationMs + " ms");
    }

    public void llmFailure(String message) {
        llmCalls.incrementAndGet();
        llmFailures.incrementAndGet();
        error("Appel LLM échoué : " + message);
    }

    public void retry(int attempt, String reason) {
        retries.incrementAndGet();
        info("Nouvelle tentative n°" + attempt + " après : " + reason);
    }

    public void cacheHit() {
        cacheHits.incrementAndGet();
    }

    public TraceSummary summary() {
        synchronized (events) {
            return new TraceSummary(Duration.between(start, Instant.now()).toMillis(), llmCalls.get(),
                    llmFailures.get(), retries.get(), cacheHits.get(), promptTokens.get(), completionTokens.get(),
                    new ArrayList<>(events));
        }
    }
}
