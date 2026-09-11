package fr.master.reviewer;

import fr.master.reviewer.application.AnalysisRequest;
import fr.master.reviewer.application.AppFactory;
import fr.master.reviewer.application.ConsoleListener;
import fr.master.reviewer.application.ReviewerException;
import fr.master.reviewer.application.ReviewerFacade;
import fr.master.reviewer.analysis.EvaluationResult;
import fr.master.reviewer.config.ConfigLoader;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.ui.MainWindow;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Point d'entrée.
 *   Interface graphique :  java -jar ai-project-reviewer.jar [--config config/reviewer.json]
 *   Ligne de commande   :  java -jar ai-project-reviewer.jar --cli --project <dossier|archive.zip|url-git>
 *                            [--profile complet] [--provider lmstudio] [--format latex] [--pdf] [--verbose]
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        Logger.getLogger("").setLevel(options.containsKey("verbose") ? Level.INFO : Level.WARNING);
        for (var handler : Logger.getLogger("").getHandlers()) {
            handler.setLevel(options.containsKey("verbose") ? Level.INFO : Level.WARNING);
        }
        Path config = Path.of(options.getOrDefault("config", "config/reviewer.json"));
        ReviewerFacade facade;
        try {
            facade = AppFactory.fromConfigFile(config);
        } catch (ConfigLoader.ConfigException | IllegalArgumentException e) {
            System.err.println("Erreur de configuration : " + e.getMessage());
            System.exit(2);
            return;
        }
        if (options.containsKey("cli") || GraphicsEnvironment.isHeadless()) {
            System.exit(runCli(facade, options));
        } else {
            SwingUtilities.invokeLater(() -> new MainWindow(facade).setVisible(true));
        }
    }

    static int runCli(ReviewerFacade facade, Map<String, String> options) {
        String source = options.get("project");
        if (source == null) {
            System.err.println("Usage : --cli --project <dossier|archive.zip|url> [--profile p] [--provider nom] "
                    + "[--format latex|html] [--pdf] [--no-commentary]");
            return 1;
        }
        try {
            Project project = facade.loadProject(source);
            System.out.println("Projet chargé : " + project.name() + " (" + project.files().count() + " fichiers)");
            String profile = options.getOrDefault("profile", facade.profiles().isEmpty() ? null : facade.profiles().get(0).name());
            EvaluationResult result = facade.analyze(new AnalysisRequest(project, profile, List.of(),
                    options.get("provider"), !options.containsKey("no-commentary")), new ConsoleListener(System.out));
            Path report = facade.generateReport(result, options.getOrDefault("format", "latex"));
            System.out.println("Rapport : " + report.toAbsolutePath());
            if (options.containsKey("pdf")) {
                facade.compilePdf(report).ifPresent(pdf -> System.out.println("PDF : " + pdf.toAbsolutePath()));
            }
            return 0;
        } catch (ReviewerException e) {
            System.err.println("Erreur : " + e.getMessage());
            return 1;
        } finally {
            facade.close();
        }
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    options.put(key, args[++i]);
                } else {
                    options.put(key, "true");
                }
            }
        }
        return options;
    }
}
