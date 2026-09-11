package fr.master.reviewer.testsupport;

import fr.master.reviewer.llm.LlmException;
import fr.master.reviewer.llm.LlmProvider;
import fr.master.reviewer.llm.LlmRequest;
import fr.master.reviewer.llm.LlmResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * DOUBLURE DE TEST ("fake") : rejoue un scénario écrit à l'avance (réponses et/ou pannes).
 * Permet de tester le moteur, la validation et la résilience sans aucun appel réseau.
 */
public final class ScriptedLlmProvider implements LlmProvider {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<LlmRequest> received = new ArrayList<>();
    private String fallbackAnswer;

    public ScriptedLlmProvider thenAnswer(String content) {
        script.add(content);
        return this;
    }

    public ScriptedLlmProvider thenFail(LlmException.Kind kind) {
        script.add(new LlmException(kind, "panne simulée"));
        return this;
    }

    /** Réponse utilisée quand le scénario est épuisé. */
    public ScriptedLlmProvider otherwiseAnswer(String content) {
        this.fallbackAnswer = content;
        return this;
    }

    @Override
    public synchronized LlmResponse ask(LlmRequest request) throws LlmException {
        received.add(request);
        Object next = script.isEmpty() ? fallbackAnswer : script.poll();
        if (next == null) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "scénario épuisé");
        }
        if (next instanceof LlmException e) {
            throw new LlmException(e.kind(), "panne simulée");
        }
        return new LlmResponse((String) next, "scripted", 10, 5, 1);
    }

    public synchronized List<LlmRequest> received() {
        return List.copyOf(received);
    }

    @Override
    public String name() {
        return "scripted";
    }

    public static String validJson(String criterion, double score) {
        return "{\"criterion\":\"" + criterion + "\",\"score\":" + score + ",\"maxScore\":10,"
                + "\"strengths\":[\"Bonne structure\"],\"weaknesses\":[\"Couplage fort\"],\"issues\":[],"
                + "\"recommendations\":[\"Introduire une interface\"]}";
    }
}
