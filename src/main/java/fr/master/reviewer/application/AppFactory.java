package fr.master.reviewer.application;

import fr.master.reviewer.analysis.CodeChunkLlmAnalyzer;
import fr.master.reviewer.analysis.ContentReader;
import fr.master.reviewer.analysis.EvaluationEngine;
import fr.master.reviewer.analysis.LlmToolkit;
import fr.master.reviewer.analysis.ResultAggregator;
import fr.master.reviewer.analysis.StructureLlmAnalyzer;
import fr.master.reviewer.analysis.StructureSummarizer;
import fr.master.reviewer.analysis.SummaryWriter;
import fr.master.reviewer.analysis.AnalyzerRegistry;
import fr.master.reviewer.analysis.deterministic.DockerfileAnalyzer;
import fr.master.reviewer.analysis.deterministic.DocumentationAnalyzer;
import fr.master.reviewer.analysis.deterministic.SandboxedBuildAnalyzer;
import fr.master.reviewer.analysis.deterministic.TestPresenceAnalyzer;
import fr.master.reviewer.config.AppConfig;
import fr.master.reviewer.config.ConfigLoader;
import fr.master.reviewer.llm.LlmProviderFactory;
import fr.master.reviewer.llm.RetryingLlmProvider;
import fr.master.reviewer.llm.parsing.LlmResultParser;
import fr.master.reviewer.llm.prompt.UntrustedContentGuard;
import fr.master.reviewer.persistence.HistoryRepository;
import fr.master.reviewer.persistence.JsonHistoryRepository;
import fr.master.reviewer.project.DirectoryProjectLoader;
import fr.master.reviewer.project.FileClassifier;
import fr.master.reviewer.project.GitProjectLoader;
import fr.master.reviewer.project.ProjectLoaderFactory;
import fr.master.reviewer.project.ZipProjectLoader;
import fr.master.reviewer.report.HtmlReportGenerator;
import fr.master.reviewer.report.LatexReportGenerator;
import fr.master.reviewer.report.PdfCompiler;
import fr.master.reviewer.report.ReportGenerator;
import fr.master.reviewer.security.DockerCommandBuilder;
import fr.master.reviewer.security.DockerSandbox;
import fr.master.reviewer.security.SandboxPolicy;
import fr.master.reviewer.security.SecretRedactor;
import fr.master.reviewer.security.SystemProcessRunner;
import fr.master.reviewer.selection.RuleBasedSelectionStrategy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * RACINE DE COMPOSITION : le SEUL endroit où les classes concrètes sont instanciées et reliées entre elles
 * (injection de dépendances "à la main", sans framework). Partout ailleurs, on ne manipule que des interfaces.
 * Conséquence : pour les tests, on reconstruit le même graphe en remplaçant ce qu'on veut (faux LLM, historique en mémoire).
 */
public final class AppFactory {

    private AppFactory() {
    }

    public static ReviewerFacade fromConfigFile(Path configPath) throws ConfigLoader.ConfigException {
        AppConfig config = new ConfigLoader().load(configPath);
        Path home = Path.of(System.getProperty("user.home"), ".ai-project-reviewer");
        return build(config, System::getenv, home.resolve("work"), new JsonHistoryRepository(home.resolve("history")),
                factory -> {
                });
    }

    /**
     * @param environment        accès aux variables d'environnement (remplaçable en test)
     * @param llmCustomizer      permet d'enregistrer d'autres types de fournisseurs (ex. faux LLM scénarisé en test)
     */
    public static ReviewerFacade build(AppConfig config, Function<String, String> environment, Path workDirectory,
                                       HistoryRepository history, Consumer<LlmProviderFactory> llmCustomizer) {
        SecretRedactor redactor = new SecretRedactor();
        ContentReader reader = ContentReader.fromDisk();

        // --- Import de projets
        DirectoryProjectLoader directoryLoader = new DirectoryProjectLoader(FileClassifier.defaultClassifier());
        ProjectLoaderFactory loaders = new ProjectLoaderFactory(List.of(
                new ZipProjectLoader(directoryLoader, workDirectory),
                new GitProjectLoader(directoryLoader, workDirectory),
                directoryLoader));

        // --- LLM
        LlmProviderFactory llmFactory = new LlmProviderFactory(config.llm(), environment, redactor, Thread::sleep);
        llmCustomizer.accept(llmFactory);
        UntrustedContentGuard guard = new UntrustedContentGuard();
        LlmToolkit toolkit = new LlmToolkit(new LlmResultParser(), guard, redactor, new ResultAggregator(),
                config.llm().outputLanguage(), config.llm().temperature(), config.llm().maxTokens(),
                config.context().maxCharsPerChunk(), config.context().maxChunksPerCriterion());

        // --- Sécurité / exécution isolée
        SystemProcessRunner processRunner = new SystemProcessRunner();
        DockerCommandBuilder dockerBuilder = new DockerCommandBuilder();
        AppConfig.SandboxSettings sb = config.sandbox() == null
                ? new AppConfig.SandboxSettings(null, null, null, 0, 0, null) : config.sandbox();
        DockerSandbox sandbox = new DockerSandbox(processRunner, dockerBuilder, new SandboxPolicy(sb.image(), sb.memory(),
                sb.cpus(), sb.pidsLimit(), Duration.ofSeconds(sb.timeoutSeconds()), false, "10001:10001", "512m"));

        // --- Analyseurs : UNE ligne par méthode d'analyse disponible
        StructureSummarizer summarizer = new StructureSummarizer();
        AnalyzerRegistry registry = new AnalyzerRegistry()
                .register("llm-code", (criterion, llm) -> new CodeChunkLlmAnalyzer(llm, toolkit))
                .register("llm-structure", (criterion, llm) -> new StructureLlmAnalyzer(llm, toolkit, summarizer))
                .register("test-presence", (criterion, llm) -> new TestPresenceAnalyzer())
                .register("documentation", (criterion, llm) -> new DocumentationAnalyzer())
                .register("dockerfile", (criterion, llm) -> new DockerfileAnalyzer())
                .register("sandbox-build", (criterion, llm) -> new SandboxedBuildAnalyzer(sandbox, sb.command()));

        List<String> unknown = registry.unknownAnalyzers(config.criteria());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Analyseur(s) inconnu(s) dans la configuration : " + unknown
                    + ". Disponibles : " + registry.keys());
        }

        RuleBasedSelectionStrategy selection = new RuleBasedSelectionStrategy(config.selection().excludeGlobs(),
                config.selection().maxFileSizeBytes());
        EvaluationEngine engine = new EvaluationEngine(registry, selection, reader,
                new SummaryWriter(guard, config.llm().outputLanguage()), config.context().parallelCriteria());

        // --- Rapports : UNE ligne par format
        Map<String, ReportGenerator> generators = new LinkedHashMap<>();
        for (ReportGenerator g : List.of(new LatexReportGenerator(), new HtmlReportGenerator())) {
            generators.put(g.formatId(), g);
        }
        PdfCompiler pdf = new PdfCompiler(PdfCompiler.Mode.valueOf(config.report().pdfMode().toUpperCase(Locale.ROOT)),
                config.report().latexImage(), processRunner, dockerBuilder);

        return new ReviewerFacade(config, loaders, llmFactory, engine, generators, pdf, history, redactor, selection.name());
    }

    /** Utilitaire pour les tests : un "sleeper" qui n'attend pas. */
    public static RetryingLlmProvider.Sleeper noSleep() {
        return ms -> {
        };
    }
}
