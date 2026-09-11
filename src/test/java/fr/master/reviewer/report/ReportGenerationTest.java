package fr.master.reviewer.report;

import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.analysis.EvaluationResult;
import fr.master.reviewer.analysis.ProjectMetrics;
import fr.master.reviewer.analysis.TraceSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGenerationTest {

    static EvaluationResult sample(String llmText) {
        CriterionResult ok = new CriterionResult("architecture", "Architecture", 8, 10, List.of("Bonne séparation"),
                List.of(llmText), List.of(), List.of("Ajouter une interface"), CriterionResult.Status.OK, "LLM : test", List.of(), 12);
        CriterionResult failed = new CriterionResult("security", "Sécurité", 0, 10, List.of(), List.of(),
                List.of("Analyse impossible : TIMEOUT"), List.of(), CriterionResult.Status.FAILED, "test", List.of(), 5);
        return new EvaluationResult("abc123", "demo_projet", "/tmp/demo", "2026-09-10T10:00:00Z", "2026-09-10T10:01:00Z",
                "complet", "lmstudio (mistral)", Map.of("Température", "0.0"), List.of(ok, failed), 16.0,
                new ProjectMetrics(3, 2, 1, 40, "A.java", 30, 1, 1, 0, true, false, true),
                "Note globale indicative : 16/20", null, new TraceSummary(60000, 2, 1, 1, 0, 100, 50, List.of("evt")));
    }

    @Test
    void escapesLatexSpecialCharacters() {
        assertEquals("100\\% \\& \\$x\\_1 \\textbackslash{}input\\{/\\allowbreak{}etc/\\allowbreak{}passwd\\}",
                LatexEscaper.escape("100% & $x_1 \\input{/etc/passwd}"));
        assertEquals("emoji ?", LatexEscaper.escape("emoji 🚀"));
        assertEquals("{[}Simulation{]} texte", LatexEscaper.escape("[Simulation] texte"), "bug trouvé en relisant le PDF");
        assertEquals("éèàçœ", LatexEscaper.escape("éèàçœ"));
    }

    @Test
    void latexReportHasRequiredSectionsAndNeutralizesInjectedCommands() {
        String tex = new LatexReportGenerator().render(sample("\\immediate\\write18{rm -rf ~} et 50% de couplage"));

        for (String section : List.of("Identification", "Configuration utilisée", "Synthèse", "Tableau récapitulatif",
                "Détail par critère", "Métriques déterministes", "Traçabilité")) {
            assertTrue(tex.contains("\\section{" + section + "}"), "section manquante : " + section);
        }
        assertTrue(tex.contains("lmstudio (mistral)"));
        assertTrue(tex.contains("demo\\_projet"));
        assertFalse(tex.contains("\\write18{"), "commande LaTeX injectée non neutralisée");
        assertTrue(tex.contains("\\textbackslash{}write18\\{rm -rf \\textasciitilde{}\\}"));
        assertTrue(tex.contains("non évalué"));
        assertTrue(tex.trim().endsWith("\\end{document}"));
    }

    @Test
    void htmlReportEscapesMarkup() {
        String html = new HtmlReportGenerator().render(sample("<script>alert(1)</script>"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
