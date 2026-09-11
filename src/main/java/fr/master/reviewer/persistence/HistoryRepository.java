package fr.master.reviewer.persistence;

import fr.master.reviewer.analysis.EvaluationResult;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Persistance des analyses. Implémentations : fichiers JSON (production), mémoire (tests). */
public interface HistoryRepository {

    void save(EvaluationResult result) throws IOException;

    List<HistoryEntry> list();

    Optional<EvaluationResult> find(String analysisId);
}
