package fr.master.reviewer.llm;

import fr.master.reviewer.analysis.AnalysisTrace;

/** PATTERN DECORATOR : ajoute la traçabilité (nombre d'appels, durée, jetons, erreurs) sans modifier l'adaptateur. */
public final class TracingLlmProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final AnalysisTrace trace;

    public TracingLlmProvider(LlmProvider delegate, AnalysisTrace trace) {
        this.delegate = delegate;
        this.trace = trace;
    }

    @Override
    public LlmResponse ask(LlmRequest request) throws LlmException {
        String what = request.meta("purpose", "?") + "/" + request.meta("criterionId", "-");
        try {
            LlmResponse response = delegate.ask(request);
            trace.llmCall(response.durationMs(), response.promptTokens(), response.completionTokens(),
                    response.model() + ", " + what);
            return response;
        } catch (LlmException e) {
            trace.llmFailure(delegate.name() + ", " + what + " -> " + e.getMessage());
            throw e;
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }
}
