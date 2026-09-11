package fr.master.reviewer.report;

import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.analysis.EvaluationResult;
import fr.master.reviewer.analysis.ProjectMetrics;
import fr.master.reviewer.analysis.TraceSummary;
import fr.master.reviewer.report.LatexDocumentBuilder.Cell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rapport LaTeX. La STRUCTURE (sections, tableaux, ordre) est décidée ici par le programme ;
 * le LLM ne fournit que du texte (points forts, recommandations, commentaire), toujours échappé.
 */
public final class LatexReportGenerator implements ReportGenerator {

    @Override
    public String formatId() {
        return "latex";
    }

    @Override
    public String fileName() {
        return "evaluation.tex";
    }

    @Override
    public String render(EvaluationResult r) {
        LatexDocumentBuilder doc = new LatexDocumentBuilder()
                .title("Rapport d'évaluation automatique", r.projectName(), r.startedAt().replace('T', ' ').replace("Z", " UTC"));

        doc.section("Identification");
        Map<String, String> id = new LinkedHashMap<>();
        id.put("Projet analysé", r.projectName());
        id.put("Source", r.projectSource());
        id.put("Identifiant d'analyse", r.analysisId());
        id.put("Date de début", r.startedAt());
        id.put("Date de fin", r.finishedAt());
        id.put("Profil d'évaluation", r.profileName());
        id.put("Modèle de langage", r.llmDescription());
        doc.keyValueTable(id);

        doc.section("Configuration utilisée").keyValueTable(new TreeMap<>(r.configuration()));

        doc.section("Synthèse")
                .bigScore("Note globale :", String.format(Locale.US, "%.1f / 20", r.overallScoreOn20()))
                .paragraph(r.summary());
        if (r.llmCommentary() != null) {
            doc.emphasis("Commentaire rédigé par le modèle de langage :", r.llmCommentary());
        }

        doc.section("Tableau récapitulatif");
        List<List<Cell>> rows = new ArrayList<>();
        for (CriterionResult c : r.results()) {
            rows.add(List.of(Cell.text(c.criterionName()),
                    Cell.text(c.status().isScored() ? format(c.score()) : "--"),
                    Cell.text(String.valueOf(c.maxScore())),
                    Cell.raw(bar(c)),
                    Cell.text(statusLabel(c.status()))));
        }
        doc.table(List.of("Critère", "Note", "Max", "Visualisation", "Statut"), "@{}p{5.2cm}rrlp{2.2cm}@{}", rows);

        doc.section("Détail par critère");
        for (CriterionResult c : r.results()) {
            doc.subsection(c.criterionName())
                    .emphasis("Note :", (c.status().isScored() ? format(c.score()) : "non évalué")
                            + " / " + c.maxScore() + " -- statut : " + statusLabel(c.status()))
                    .emphasis("Méthode :", c.source() + " -- " + c.durationMs() + " ms")
                    .list("Points forts", c.strengths())
                    .list("Points faibles", c.weaknesses())
                    .list("Problèmes identifiés", c.issues())
                    .list("Recommandations", c.recommendations())
                    .smallList("Avertissements de l'outil", c.warnings());
        }

        doc.section("Métriques déterministes").paragraph("Mesures calculées par le programme, sans modèle de langage.")
                .keyValueTable(metrics(r.metrics()));

        doc.section("Traçabilité").keyValueTable(trace(r.trace()));
        List<String> events = r.trace().events();
        doc.smallList("Derniers événements", events.subList(Math.max(0, events.size() - 25), events.size()));

        doc.section("Limites de cette évaluation").paragraph("Ce rapport est produit automatiquement. Les parties issues "
                + "d'un modèle de langage ne sont pas déterministes et peuvent contenir des erreurs : elles constituent une aide "
                + "à l'évaluation et non une note définitive. Seule une partie des fichiers peut avoir été transmise au modèle "
                + "(voir les avertissements). Les critères en échec sont exclus de la note globale.");
        return doc.build();
    }

    private static String bar(CriterionResult c) {
        if (!c.status().isScored()) {
            return "\\textcolor{black!40}{" + (c.status() == CriterionResult.Status.FAILED ? "non évalué" : "non applicable") + "}";
        }
        double ratio = Math.max(0, Math.min(1, c.ratio()));
        String color = ratio >= 0.7 ? "scoregood" : ratio >= 0.5 ? "scoremid" : "scorelow";
        return String.format(Locale.US, "\\textcolor{%s}{\\rule{%.2fcm}{7pt}}\\textcolor{black!12}{\\rule{%.2fcm}{7pt}}",
                color, 3 * ratio, 3 * (1 - ratio));
    }

    private static Map<String, String> metrics(ProjectMetrics m) {
        Map<String, String> map = new LinkedHashMap<>();
        if (m == null) {
            return map;
        }
        map.put("Fichiers", String.valueOf(m.totalFiles()));
        map.put("Fichiers source Java", String.valueOf(m.javaFiles()));
        map.put("Fichiers de test", String.valueOf(m.testFiles()));
        map.put("Lignes de code Java", String.valueOf(m.javaLines()));
        map.put("Plus gros fichier", m.largestJavaFile() + " (" + m.largestJavaFileLines() + " lignes)");
        map.put("Blocs catch vides", String.valueOf(m.emptyCatchBlocks()));
        map.put("catch (Exception/Throwable)", String.valueOf(m.genericCatches()));
        map.put("Marqueurs TODO/FIXME", String.valueOf(m.todoCount()));
        map.put("README / Dockerfile / build", yes(m.hasReadme()) + " / " + yes(m.hasDockerfile()) + " / " + yes(m.hasBuildFile()));
        return map;
    }

    private static Map<String, String> trace(TraceSummary t) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Durée totale", String.format(Locale.US, "%.1f s", t.durationMs() / 1000.0));
        map.put("Appels au LLM", String.valueOf(t.llmCalls()));
        map.put("Appels échoués", String.valueOf(t.llmFailures()));
        map.put("Nouvelles tentatives", String.valueOf(t.retries()));
        map.put("Réponses servies par le cache", String.valueOf(t.cacheHits()));
        map.put("Jetons (prompt / réponse)", t.promptTokens() + " / " + t.completionTokens());
        return map;
    }

    private static String yes(boolean b) {
        return b ? "oui" : "non";
    }

    private static String format(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }

    static String statusLabel(CriterionResult.Status s) {
        return switch (s) {
            case OK -> "complet";
            case PARTIAL -> "partiel";
            case FAILED -> "échec";
            case NOT_APPLICABLE -> "non applicable";
        };
    }
}
