package fr.master.reviewer.application;

import fr.master.reviewer.analysis.AnalysisListener;
import fr.master.reviewer.analysis.AnalysisTrace;
import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.analysis.EvaluationEngine;
import fr.master.reviewer.analysis.EvaluationResult;
import fr.master.reviewer.config.AppConfig;
import fr.master.reviewer.llm.LlmException;
import fr.master.reviewer.llm.LlmProvider;
import fr.master.reviewer.llm.LlmProviderFactory;
import fr.master.reviewer.persistence.HistoryEntry;
import fr.master.reviewer.persistence.HistoryRepository;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.project.ProjectLoadException;
import fr.master.reviewer.project.ProjectLoaderFactory;
import fr.master.reviewer.report.PdfCompiler;
import fr.master.reviewer.report.ReportGenerator;
import fr.master.reviewer.security.SecretRedactor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PATTERN FACADE : point d'entrée UNIQUE de l'IHM (et de la ligne de commande) vers le cœur applicatif.
 * L'IHM appelle 6 méthodes simples ; elle ignore l'existence des chargeurs, décorateurs LLM, analyseurs,
 * générateurs, etc. On peut ainsi remplacer Swing par JavaFX ou par une API web sans toucher au cœur.
 * C'est aussi la couche "cas d'utilisation" : importer, analyser, générer un rapport, consulter l'historique.
 */
public final class ReviewerFacade implements AutoCloseable {

    private final AppConfig config;
    private final ProjectLoaderFactory loaders;
    private final LlmProviderFactory llmFactory;
    private final EvaluationEngine engine;
    private final Map<String, ReportGenerator> reportGenerators;
    private final PdfCompiler pdfCompiler;
    private final HistoryRepository history;
    private final SecretRedactor redactor;
    private final String selectionDescription;
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "analysis-worker");
        t.setDaemon(true);
        return t;
    });

    public ReviewerFacade(AppConfig config, ProjectLoaderFactory loaders, LlmProviderFactory llmFactory,
                          EvaluationEngine engine, Map<String, ReportGenerator> reportGenerators, PdfCompiler pdfCompiler,
                          HistoryRepository history, SecretRedactor redactor, String selectionDescription) {
        this.config = config;
        this.loaders = loaders;
        this.llmFactory = llmFactory;
        this.engine = engine;
        this.reportGenerators = new LinkedHashMap<>(reportGenerators);
        this.pdfCompiler = pdfCompiler;
        this.history = history;
        this.redactor = redactor;
        this.selectionDescription = selectionDescription;
    }

    // ---------- Cas d'utilisation 1 : importer un projet ----------

    public Project loadProject(String source) throws ReviewerException {
        try {
            return loaders.load(source);
        } catch (ProjectLoadException e) {
            throw new ReviewerException(e.getMessage(), e);
        }
    }

    // ---------- Informations pour l'IHM ----------

    public List<Criterion> criteria() {
        return config.criteria();
    }

    public List<AppConfig.Profile> profiles() {
        return config.profiles();
    }

    public List<String> criteriaOfProfile(String profileName) {
        return config.profiles().stream().filter(p -> p.name().equals(profileName)).findFirst()
                .map(AppConfig.Profile::criteria)
                .orElse(config.criteria().stream().map(Criterion::id).toList());
    }

    public List<String> providers() {
        return llmFactory.providerNames();
    }

    public String defaultProvider() {
        return llmFactory.activeName();
    }

    public List<String> reportFormats() {
        return List.copyOf(reportGenerators.keySet());
    }

    public boolean pdfEnabled() {
        return pdfCompiler.mode() != PdfCompiler.Mode.NONE;
    }

    // ---------- Cas d'utilisation 2 : analyser ----------

    public EvaluationResult analyze(AnalysisRequest request, AnalysisListener listener) throws ReviewerException {
        List<Criterion> selected = resolveCriteria(request);
        if (selected.isEmpty()) {
            throw new ReviewerException("Aucun critère sélectionné");
        }
        AnalysisTrace trace = new AnalysisTrace(redactor);
        LlmProvider llm;
        try {
            llm = llmFactory.create(request.providerName(), trace);
        } catch (LlmException e) {
            throw new ReviewerException("Configuration LLM invalide : " + e.getMessage(), e);
        }
        EvaluationResult result = engine.evaluate(new EvaluationEngine.Session(request.project(), selected, llm,
                request.profileName() == null ? "personnalisé" : request.profileName(), describeConfiguration(request),
                listener, trace, request.llmCommentary()));
        try {
            history.save(result);
        } catch (IOException e) {
            listener.warning("Historique non sauvegardé : " + e.getMessage());
        }
        return result;
    }

    /** Version asynchrone pour l'IHM : l'analyse tourne hors du thread graphique. */
    public CompletableFuture<EvaluationResult> analyzeAsync(AnalysisRequest request, AnalysisListener listener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return analyze(request, listener);
            } catch (ReviewerException e) {
                throw new CompletionException(e);
            }
        }, background);
    }

    // ---------- Cas d'utilisation 3 : produire le rapport ----------

    public Path generateReport(EvaluationResult result, String format) throws ReviewerException {
        ReportGenerator generator = reportGenerators.get(format);
        if (generator == null) {
            throw new ReviewerException("Format de rapport inconnu : " + format);
        }
        String folder = result.projectName().replaceAll("[^\\w.-]", "_") + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        try {
            return generator.write(result, Path.of(config.report().outputDirectory()).resolve(folder));
        } catch (IOException e) {
            throw new ReviewerException("Écriture du rapport impossible : " + e.getMessage(), e);
        }
    }

    public Optional<Path> compilePdf(Path texFile) throws ReviewerException {
        try {
            return pdfCompiler.compile(texFile);
        } catch (IOException e) {
            throw new ReviewerException("Compilation PDF impossible : " + e.getMessage(), e);
        }
    }

    // ---------- Cas d'utilisation 4 : historique ----------

    public List<HistoryEntry> history() {
        return history.list();
    }

    public Optional<EvaluationResult> historyResult(String analysisId) {
        return history.find(analysisId);
    }

    private List<Criterion> resolveCriteria(AnalysisRequest request) {
        List<String> ids = request.criterionIds().isEmpty() ? criteriaOfProfile(request.profileName()) : request.criterionIds();
        return config.criteria().stream().filter(c -> ids.contains(c.id())).toList();
    }

    private Map<String, String> describeConfiguration(AnalysisRequest request) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Fournisseur LLM demandé", request.providerName() == null ? config.llm().active() : request.providerName());
        m.put("Fournisseur de repli", config.llm().fallback() == null ? "aucun" : config.llm().fallback());
        m.put("Température", String.valueOf(config.llm().temperature()));
        m.put("Tentatives max par appel", String.valueOf(config.llm().maxAttempts()));
        m.put("Cache des réponses", config.llm().cacheEnabled() ? "activé" : "désactivé");
        m.put("Taille max d'un segment (caractères)", String.valueOf(config.context().maxCharsPerChunk()));
        m.put("Segments max par critère", String.valueOf(config.context().maxChunksPerCriterion()));
        m.put("Critères en parallèle", String.valueOf(config.context().parallelCriteria()));
        m.put("Sélection des fichiers", selectionDescription);
        return m;
    }

    @Override
    public void close() {
        background.shutdownNow();
    }
}
