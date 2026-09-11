package fr.master.reviewer.analysis;

import java.util.List;

/** Instantané de la traçabilité d'une analyse (sauvegardé dans l'historique et affiché dans le rapport). */
public record TraceSummary(long durationMs, int llmCalls, int llmFailures, int retries, int cacheHits,
                           long promptTokens, long completionTokens, List<String> events) {

    public TraceSummary {
        events = List.copyOf(events == null ? List.of() : events);
    }
}
