package fr.master.reviewer.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.master.reviewer.analysis.EvaluationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Historique sous forme d'un fichier JSON par analyse (lisible, versionnable, sans base de données). */
public final class JsonHistoryRepository implements HistoryRepository {

    private static final Logger LOG = Logger.getLogger(JsonHistoryRepository.class.getName());

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonHistoryRepository(Path directory) {
        this.directory = directory;
    }

    @Override
    public void save(EvaluationResult result) throws IOException {
        Files.createDirectories(directory);
        String name = result.startedAt().replaceAll("[^0-9T]", "") + "-" + result.analysisId() + ".json";
        mapper.writeValue(directory.resolve(name).toFile(), result);
    }

    @Override
    public List<HistoryEntry> list() {
        return readAll().stream().map(JsonHistoryRepository::toEntry).toList();
    }

    @Override
    public Optional<EvaluationResult> find(String analysisId) {
        return readAll().stream().filter(r -> r.analysisId().equals(analysisId)).findFirst();
    }

    private List<EvaluationResult> readAll() {
        List<EvaluationResult> results = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return results;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder()).toList()) {
                try {
                    results.add(mapper.readValue(f.toFile(), EvaluationResult.class));
                } catch (IOException e) {
                    LOG.warning("Entrée d'historique illisible ignorée : " + f.getFileName());
                }
            }
        } catch (IOException e) {
            LOG.warning("Historique illisible : " + e.getMessage());
        }
        return results;
    }

    static HistoryEntry toEntry(EvaluationResult r) {
        return new HistoryEntry(r.analysisId(), r.projectName(), r.startedAt(), r.profileName(), r.llmDescription(),
                r.overallScoreOn20(), r.failedCount());
    }
}
