package fr.master.reviewer.analysis;

import fr.master.reviewer.testsupport.ScriptedLlmProvider;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzerRegistryTest {

    private final Analyzer dummy = ctx -> null;

    @Test
    void refusesDuplicateKeys() {
        AnalyzerRegistry registry = new AnalyzerRegistry().register("llm-code", (c, llm) -> dummy);
        assertThrows(IllegalStateException.class, () -> registry.register("llm-code", (c, llm) -> dummy));
    }

    @Test
    void detectsUnknownAnalyzersBeforeAnyAnalysis() {
        AnalyzerRegistry registry = new AnalyzerRegistry().register("llm-code", (c, llm) -> dummy);
        List<Criterion> criteria = List.of(TestData.criterion("readability", "llm-code"), TestData.criterion("naming", "llm-cod"));

        assertEquals(List.of("critère 'naming' -> analyseur 'llm-cod'"), registry.unknownAnalyzers(criteria));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry.create(criteria.get(1), new ScriptedLlmProvider()));
        assertTrue(e.getMessage().contains("llm-code"), "le message liste les analyseurs disponibles");
    }
}
