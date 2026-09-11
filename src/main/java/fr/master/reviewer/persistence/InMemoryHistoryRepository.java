package fr.master.reviewer.persistence;

import fr.master.reviewer.analysis.EvaluationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Implémentation en mémoire : utile pour les tests (aucun fichier écrit). */
public final class InMemoryHistoryRepository implements HistoryRepository {

    private final List<EvaluationResult> results = new CopyOnWriteArrayList<>();

    @Override
    public void save(EvaluationResult result) {
        results.add(result);
    }

    @Override
    public List<HistoryEntry> list() {
        List<HistoryEntry> entries = new ArrayList<>();
        for (int i = results.size() - 1; i >= 0; i--) {
            entries.add(JsonHistoryRepository.toEntry(results.get(i)));
        }
        return entries;
    }

    @Override
    public Optional<EvaluationResult> find(String analysisId) {
        return results.stream().filter(r -> r.analysisId().equals(analysisId)).findFirst();
    }
}
