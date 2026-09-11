package fr.master.reviewer.llm.parsing;

import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.llm.LlmException;
import fr.master.reviewer.testsupport.ScriptedLlmProvider;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmResultParserTest {

    private final LlmResultParser parser = new LlmResultParser();
    private final Criterion criterion = TestData.criterion("architecture", "llm-structure");

    @Test
    void parsesValidResponse() throws Exception {
        LlmEvaluation e = parser.parse(ScriptedLlmProvider.validJson("architecture", 7), criterion);
        assertEquals(7.0, e.score());
        assertEquals("Bonne structure", e.strengths().get(0));
        assertTrue(e.warnings().isEmpty());
    }

    @Test
    void toleratesMarkdownFencesThinkingTagsAndSurroundingText() throws Exception {
        String raw = "<think>je réfléchis { pas du json }</think>Voici :\n```json\n"
                + ScriptedLlmProvider.validJson("architecture", 6.5) + "\n```\nBonne journée";
        assertEquals(6.5, parser.parse(raw, criterion).score());
    }

    @Test
    void handlesBracesInsideStrings() throws Exception {
        String raw = "{\"score\":5,\"strengths\":[\"utilise des { et } dans le texte\"],\"weaknesses\":[],\"recommendations\":[]}";
        assertEquals("utilise des { et } dans le texte", parser.parse(raw, criterion).strengths().get(0));
    }

    @Test
    void convertsOtherScaleWithWarning() throws Exception {
        String raw = "{\"score\":80,\"maxScore\":100,\"strengths\":[],\"weaknesses\":[],\"recommendations\":[]}";
        LlmEvaluation e = parser.parse(raw, criterion);
        assertEquals(8.0, e.score());
        assertFalse(e.warnings().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Je ne peux pas répondre",                                                        // pas de JSON
            "{\"score\": 7, \"strengths\": [",                                                // tronqué
            "{\"strengths\":[],\"weaknesses\":[],\"recommendations\":[]}",                    // score absent
            "{\"score\":\"sept\",\"strengths\":[],\"weaknesses\":[],\"recommendations\":[]}",  // score non numérique
            "{\"score\":15,\"strengths\":[],\"weaknesses\":[],\"recommendations\":[]}",       // hors bornes
            "{\"score\":-1,\"strengths\":[],\"weaknesses\":[],\"recommendations\":[]}",       // négatif
            "{\"score\":5,\"strengths\":\"bien\",\"weaknesses\":[],\"recommendations\":[]}",  // pas une liste
            "{\"score\":5,\"strengths\":[1,2],\"weaknesses\":[],\"recommendations\":[]}",     // liste non textuelle
            "{\"score\":5,\"strengths\":[]}"                                                  // champs manquants
    })
    void rejectsInvalidResponses(String raw) {
        LlmException e = assertThrows(LlmException.class, () -> parser.parse(raw, criterion));
        assertEquals(LlmException.Kind.INVALID_SCHEMA, e.kind());
    }

    @Test
    void rejectsEmptyResponse() {
        LlmException e = assertThrows(LlmException.class, () -> parser.parse("   ", criterion));
        assertEquals(LlmException.Kind.EMPTY_RESPONSE, e.kind());
    }

    @Test
    void flagsInconsistentAnswer() throws Exception {
        String raw = "{\"score\":10,\"strengths\":[],\"weaknesses\":[\"a\",\"b\",\"c\"],\"issues\":[\"d\"],\"recommendations\":[]}";
        assertFalse(parser.parse(raw, criterion).warnings().isEmpty());
    }
}
