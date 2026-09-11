package fr.master.reviewer.llm;

import fr.master.reviewer.analysis.AnalysisTrace;
import fr.master.reviewer.testsupport.ScriptedLlmProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryingLlmProviderTest {

    private final LlmRequest request = new LlmRequest("s", "u", 0, 100, Map.of());
    private final List<Long> sleeps = new ArrayList<>();

    @Test
    void retriesTransientFailuresWithExponentialBackoff() throws Exception {
        ScriptedLlmProvider fake = new ScriptedLlmProvider()
                .thenFail(LlmException.Kind.TIMEOUT).thenFail(LlmException.Kind.UNAVAILABLE).thenAnswer("ok");
        AnalysisTrace trace = AnalysisTrace.noop();
        RetryingLlmProvider provider = new RetryingLlmProvider(fake, 3, 100, trace, sleeps::add);

        assertEquals("ok", provider.ask(request).content());
        assertEquals(List.of(100L, 200L), sleeps);
        assertEquals(2, trace.summary().retries());
    }

    @Test
    void stopsAfterMaxAttempts() {
        ScriptedLlmProvider fake = new ScriptedLlmProvider().otherwiseAnswer(null)
                .thenFail(LlmException.Kind.TIMEOUT).thenFail(LlmException.Kind.TIMEOUT).thenFail(LlmException.Kind.TIMEOUT);
        RetryingLlmProvider provider = new RetryingLlmProvider(fake, 3, 1, AnalysisTrace.noop(), sleeps::add);

        assertThrows(LlmException.class, () -> provider.ask(request));
        assertEquals(3, fake.received().size());
    }

    @Test
    void doesNotRetryPermanentErrors() {
        ScriptedLlmProvider fake = new ScriptedLlmProvider().thenFail(LlmException.Kind.CONFIGURATION).thenAnswer("jamais");
        RetryingLlmProvider provider = new RetryingLlmProvider(fake, 5, 1, AnalysisTrace.noop(), sleeps::add);

        LlmException e = assertThrows(LlmException.class, () -> provider.ask(request));
        assertEquals(LlmException.Kind.CONFIGURATION, e.kind());
        assertEquals(1, fake.received().size());
    }
}
