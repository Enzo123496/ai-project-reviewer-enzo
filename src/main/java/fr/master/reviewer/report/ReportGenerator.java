package fr.master.reviewer.report;

import fr.master.reviewer.analysis.EvaluationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Transforme un EvaluationResult (modèle Java) en document. Nouveau type de rapport (HTML, Markdown,
 * JSON...) = nouvelle implémentation enregistrée dans AppFactory, sans toucher au moteur ni à l'IHM.
 */
public interface ReportGenerator {

    /** Identifiant affiché dans l'IHM, ex. "latex". */
    String formatId();

    /** Nom du fichier produit, ex. "evaluation.tex". */
    String fileName();

    String render(EvaluationResult result);

    default Path write(EvaluationResult result, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(fileName());
        Files.writeString(file, render(result), StandardCharsets.UTF_8);
        return file;
    }
}
