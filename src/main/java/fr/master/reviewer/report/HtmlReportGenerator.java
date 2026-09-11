package fr.master.reviewer.report;

import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.analysis.EvaluationResult;

import java.util.List;
import java.util.Locale;

/**
 * Deuxième format de rapport : il démontre l'extensibilité (aucune autre classe n'a été modifiée,
 * seulement une ligne d'enregistrement dans AppFactory). Tout le texte est échappé (anti-XSS).
 */
public final class HtmlReportGenerator implements ReportGenerator {

    @Override
    public String formatId() {
        return "html";
    }

    @Override
    public String fileName() {
        return "evaluation.html";
    }

    @Override
    public String render(EvaluationResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"utf-8\"><title>Évaluation - ")
                .append(esc(r.projectName())).append("</title><style>body{font-family:sans-serif;max-width:900px;margin:auto;padding:1em}"
                        + "table{border-collapse:collapse;width:100%}td,th{border-bottom:1px solid #ddd;padding:6px;text-align:left}"
                        + ".bar{background:#eee;width:150px;height:10px}.fill{background:#2e7d32;height:10px}</style></head><body>");
        sb.append("<h1>Rapport d'évaluation : ").append(esc(r.projectName())).append("</h1>");
        sb.append("<p>").append(esc(r.startedAt())).append(" — modèle : ").append(esc(r.llmDescription()))
                .append(" — profil : ").append(esc(r.profileName())).append("</p>");
        sb.append("<h2>Note globale : ").append(String.format(Locale.US, "%.1f", r.overallScoreOn20())).append(" / 20</h2>");
        sb.append("<p>").append(esc(r.summary())).append("</p>");
        if (r.llmCommentary() != null) {
            sb.append("<blockquote>").append(esc(r.llmCommentary())).append("</blockquote>");
        }
        sb.append("<table><tr><th>Critère</th><th>Note</th><th></th><th>Statut</th></tr>");
        for (CriterionResult c : r.results()) {
            sb.append("<tr><td>").append(esc(c.criterionName())).append("</td><td>").append(c.score()).append(" / ")
                    .append(c.maxScore()).append("</td><td><div class=\"bar\"><div class=\"fill\" style=\"width:")
                    .append(Math.round(c.ratio() * 100)).append("%\"></div></div></td><td>")
                    .append(LatexReportGenerator.statusLabel(c.status())).append("</td></tr>");
        }
        sb.append("</table>");
        for (CriterionResult c : r.results()) {
            sb.append("<h3>").append(esc(c.criterionName())).append("</h3>");
            list(sb, "Points forts", c.strengths());
            list(sb, "Points faibles", c.weaknesses());
            list(sb, "Problèmes", c.issues());
            list(sb, "Recommandations", c.recommendations());
        }
        return sb.append("</body></html>").toString();
    }

    private static void list(StringBuilder sb, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append("<h4>").append(title).append("</h4><ul>");
        items.forEach(i -> sb.append("<li>").append(esc(i)).append("</li>"));
        sb.append("</ul>");
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
