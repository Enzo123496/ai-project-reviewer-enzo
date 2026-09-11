package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.LlmException;
import fr.master.reviewer.llm.LlmProvider;
import fr.master.reviewer.llm.LlmRequest;
import fr.master.reviewer.llm.LlmResponse;
import fr.master.reviewer.llm.parsing.LlmEvaluation;
import fr.master.reviewer.llm.prompt.PromptBuilder;
import fr.master.reviewer.project.FileNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PATTERN TEMPLATE METHOD : l'algorithme d'une analyse par LLM est TOUJOURS le même
 *   1. sélectionner les fichiers  2. détecter les injections  3. préparer les segments (VARIABLE)
 *   4. construire le prompt  5. appeler le LLM  6. valider (+ 1 demande de correction)  7. agréger
 * La méthode analyze() est "final" : les sous-classes ne peuvent changer que les étapes prévues
 * (prepareSegments obligatoire ; role/facts optionnels). Les garanties de sécurité et de robustesse
 * (validation, reprise partielle, détection d'injection) sont donc héritées par TOUS les analyseurs LLM.
 */
public abstract class AbstractLlmAnalyzer implements Analyzer {

    /** Un morceau de contenu à envoyer, avec une étiquette lisible. */
    public record Segment(String label, String content) {
    }

    public record SegmentPlan(List<Segment> segments, List<String> warnings) {
    }

    private record Answer(LlmEvaluation evaluation, String model) {
    }

    private static final CitationChecker CITATIONS = new CitationChecker();

    protected final LlmProvider llm;
    protected final LlmToolkit toolkit;

    protected AbstractLlmAnalyzer(LlmProvider llm, LlmToolkit toolkit) {
        this.llm = llm;
        this.toolkit = toolkit;
    }

    @Override
    public final CriterionResult analyze(AnalysisContext ctx) {
        Criterion criterion = ctx.criterion();
        List<FileNode> files = ctx.selection().select(ctx.project(), criterion);
        if (files.isEmpty()) {
            // Rien à évaluer : "non applicable" plutôt qu'un 0 qui fausserait la note globale. Aucun appel LLM.
            ctx.trace().info("Critère " + criterion.id() + " : aucun fichier pertinent, aucun appel LLM");
            return CriterionResult.notApplicable(criterion, "aucun fichier pertinent pour ce critère");
        }

        List<String> injections = detectInjections(ctx, files);
        SegmentPlan plan = prepareSegments(ctx, files);
        List<String> warnings = new ArrayList<>(plan.warnings());
        List<ResultAggregator.Part> evaluations = new ArrayList<>();
        Set<String> models = new LinkedHashSet<>();
        int failed = 0;
        String lastError = "aucun segment";
        int total = plan.segments().size();

        for (int i = 0; i < total; i++) {
            Segment segment = plan.segments().get(i);
            ctx.listener().progress(criterion, "Segment " + (i + 1) + "/" + total + " envoyé au LLM");
            try {
                Answer answer = askAndValidate(ctx, buildRequest(ctx, segment, i + 1, total));
                evaluations.add(new ResultAggregator.Part(answer.evaluation(), segment.content().length()));
                models.add(answer.model());
            } catch (LlmException e) {
                failed++;
                lastError = e.getMessage();
                warnings.add("Segment " + (i + 1) + "/" + total + " ignoré : " + e.getMessage());
                ctx.trace().error("Critère " + criterion.id() + ", segment " + (i + 1) + " : " + e.getMessage());
                if (e.kind() == LlmException.Kind.INTERRUPTED || e.kind() == LlmException.Kind.CONFIGURATION) {
                    break; // inutile d'insister sur les segments suivants
                }
            }
        }

        if (evaluations.isEmpty()) {
            List<String> issues = new ArrayList<>(List.of("Analyse impossible : " + lastError));
            injections.forEach(f -> issues.add("Tentative possible d'injection de prompt : " + f));
            return new CriterionResult(criterion.id(), criterion.name(), 0, criterion.maxScore(), List.of(),
                    List.of(), issues, List.of(), CriterionResult.Status.FAILED, llm.name(), warnings, 0);
        }
        String source = "LLM : " + String.join(", ", models) + " (" + evaluations.size() + "/" + total + " segment(s))";
        CriterionResult result = toolkit.aggregator().aggregate(criterion, evaluations, failed, warnings, injections, source);
        return checkCitations(ctx, result);
    }

    /** Signale les fichiers cités par le modèle qui n'existent pas dans le projet (hallucinations). */
    private CriterionResult checkCitations(AnalysisContext ctx, CriterionResult result) {
        List<String> texts = new ArrayList<>();
        texts.addAll(result.strengths());
        texts.addAll(result.weaknesses());
        texts.addAll(result.issues());
        texts.addAll(result.recommendations());
        List<String> unknown = CITATIONS.unknownCitations(ctx.project(), texts);
        if (unknown.isEmpty()) {
            return result;
        }
        ctx.trace().error("Critère " + result.criterionId() + " : fichiers cités introuvables " + unknown);
        return result.withAdditionalWarnings(List.of("Le modèle cite des fichiers introuvables dans le projet "
                + "(hallucination probable, remarques à vérifier) : " + String.join(", ", unknown)));
    }

    /** Étape VARIABLE : comment transformer les fichiers en segments à envoyer. */
    protected abstract SegmentPlan prepareSegments(AnalysisContext ctx, List<FileNode> files);

    /** Crochet (hook) : rôle donné au modèle. */
    protected String role(Criterion criterion) {
        return PromptBuilder.DEFAULT_ROLE;
    }

    /** Crochet : faits déterministes fournis au modèle. */
    protected Map<String, String> facts(AnalysisContext ctx) {
        return ctx.metrics().asFacts();
    }

    private List<String> detectInjections(AnalysisContext ctx, List<FileNode> files) {
        List<String> findings = new ArrayList<>();
        for (FileNode f : files) {
            findings.addAll(toolkit.guard().detectInjections(f.relativePath(), ctx.reader().read(f)));
        }
        if (!findings.isEmpty()) {
            ctx.listener().warning("Injection de prompt suspectée dans " + findings.size() + " ligne(s) (critère "
                    + ctx.criterion().id() + ")");
            ctx.trace().error("Injection de prompt suspectée : " + findings);
        }
        return findings.stream().limit(10).toList();
    }

    private LlmRequest buildRequest(AnalysisContext ctx, Segment segment, int index, int total) {
        return PromptBuilder.forCriterion(ctx.criterion(), toolkit.guard())
                .role(role(ctx.criterion()))
                .projectName(ctx.project().name())
                .facts(facts(ctx))
                .untrustedContent(segment.label(), toolkit.redactor().redact(segment.content()))
                .segment(index, total)
                .outputLanguage(toolkit.outputLanguage())
                .sampling(toolkit.temperature(), toolkit.maxTokens())
                .build();
    }

    /** Appel + validation. Si la réponse est invalide : UNE relance expliquant l'erreur au modèle. */
    private Answer askAndValidate(AnalysisContext ctx, LlmRequest request) throws LlmException {
        LlmResponse response = llm.ask(request);
        try {
            return new Answer(toolkit.parser().parse(response.content(), ctx.criterion()), response.model());
        } catch (LlmException invalid) {
            ctx.trace().info("Réponse invalide (" + invalid.getMessage() + "), demande de correction au modèle");
            LlmResponse retry = llm.ask(PromptBuilder.repairRequest(request, invalid.getMessage()));
            return new Answer(toolkit.parser().parse(retry.content(), ctx.criterion()), retry.model());
        }
    }
}
