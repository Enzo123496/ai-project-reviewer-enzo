package fr.master.reviewer.llm;

import fr.master.reviewer.analysis.AnalysisTrace;

import java.util.List;

/**
 * Stratégie de repli : si le fournisseur principal échoue définitivement (ex. LM Studio arrêté),
 * on essaie le suivant (ex. API Mistral). La réponse indique quel modèle a réellement répondu.
 */
public final class FallbackLlmProvider implements LlmProvider {

    private final List<LlmProvider> providers;
    private final AnalysisTrace trace;

    public FallbackLlmProvider(List<LlmProvider> providers, AnalysisTrace trace) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("Au moins un fournisseur est requis");
        }
        this.providers = List.copyOf(providers);
        this.trace = trace;
    }

    @Override
    public LlmResponse ask(LlmRequest request) throws LlmException {
        LlmException last = null;
        for (LlmProvider provider : providers) {
            try {
                return provider.ask(request);
            } catch (LlmException e) {
                if (e.kind() == LlmException.Kind.INTERRUPTED) {
                    throw e;
                }
                last = e;
                trace.error("Fournisseur " + provider.name() + " en échec, passage au suivant : " + e.getMessage());
            }
        }
        throw new LlmException(LlmException.Kind.UNAVAILABLE, "tous les fournisseurs ont échoué (dernier : "
                + last.getMessage() + ")", false, last);
    }

    @Override
    public String name() {
        return providers.get(0).name() + " (repli : " + providers.subList(1, providers.size()).stream()
                .map(LlmProvider::name).reduce((a, b) -> a + ", " + b).orElse("aucun") + ")";
    }
}
