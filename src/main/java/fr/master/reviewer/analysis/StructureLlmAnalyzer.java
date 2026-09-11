package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.LlmProvider;
import fr.master.reviewer.project.FileNode;

import java.util.List;

/**
 * Analyse par LLM sur la STRUCTURE du projet (architecture, organisation, modularité, couplage).
 * Au lieu du code complet, on envoie un résumé déterministe : un seul appel, même pour un gros projet.
 */
public final class StructureLlmAnalyzer extends AbstractLlmAnalyzer {

    private final StructureSummarizer summarizer;

    public StructureLlmAnalyzer(LlmProvider llm, LlmToolkit toolkit, StructureSummarizer summarizer) {
        super(llm, toolkit);
        this.summarizer = summarizer;
    }

    @Override
    protected SegmentPlan prepareSegments(AnalysisContext ctx, List<FileNode> files) {
        int budget = toolkit.maxCharsPerChunk() * 2;
        StructureSummarizer.Summary summary = summarizer.summarize(ctx.project(), files, ctx.reader(), budget);
        List<String> warnings = new java.util.ArrayList<>();
        if (summary.level() != StructureSummarizer.DetailLevel.FULL) {
            warnings.add("Projet volumineux : résumé de structure au niveau « " + summary.level().label()
                    + " » (méthodes non transmises au modèle)");
        }
        if (summary.truncated()) {
            warnings.add("Résumé de structure tronqué à " + budget + " caractères : une partie des fichiers n'a pas été transmise");
        }
        return new SegmentPlan(List.of(new Segment("structural summary (tree, types, public API, dependencies)",
                summary.text())), warnings);
    }

    @Override
    protected String role(Criterion criterion) {
        return "You are a senior software architect. You judge responsibilities, layering, coupling, cohesion and "
                + "design patterns from a structural summary of a Java project written by master's students.";
    }
}
