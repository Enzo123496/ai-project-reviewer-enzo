package fr.master.reviewer.llm;

import fr.master.reviewer.analysis.AnalysisTrace;
import fr.master.reviewer.testsupport.ScriptedLlmProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FallbackAndCacheTest {

    private final LlmRequest request = new LlmRequest("s", "u", 0, 100, Map.of());

    @Test
    void fallsBackToSecondProvider() throws Exception {
        ScriptedLlmProvider primary = new ScriptedLlmProvider().thenFail(LlmException.Kind.UNAVAILABLE);
        ScriptedLlmProvider secondary = new ScriptedLlmProvider().thenAnswer("secours");
        LlmProvider provider = new FallbackLlmProvider(List.of(primary, secondary), AnalysisTrace.noop());

        assertEquals("secours", provider.ask(request).content());
    }

    @Test
    void failsWhenAllProvidersFail() {
        LlmProvider provider = new FallbackLlmProvider(List.of(
                new ScriptedLlmProvider().thenFail(LlmException.Kind.TIMEOUT),
                new ScriptedLlmProvider().thenFail(LlmException.Kind.HTTP_ERROR)), AnalysisTrace.noop());

        assertEquals(LlmException.Kind.UNAVAILABLE, assertThrows(LlmException.class, () -> provider.ask(request)).kind());
    }

    @Test
    void cacheAvoidsSecondCall() throws Exception {
        ScriptedLlmProvider fake = new ScriptedLlmProvider().thenAnswer("une fois");
        AnalysisTrace trace = AnalysisTrace.noop();
        LlmProvider provider = new CachingLlmProvider(fake, new HashMap<>(), trace);

        provider.ask(request);
        assertEquals("une fois", provider.ask(request).content());
        assertEquals(1, fake.received().size());
        assertEquals(1, trace.summary().cacheHits());
    }
}
