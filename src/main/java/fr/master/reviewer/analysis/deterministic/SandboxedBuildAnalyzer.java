package fr.master.reviewer.analysis.deterministic;

import fr.master.reviewer.analysis.AnalysisContext;
import fr.master.reviewer.analysis.Analyzer;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.security.DockerSandbox;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compile et lance les tests du projet évalué DANS le bac à sable Docker (sans réseau par défaut).
 * C'est le seul endroit où du code non fiable est exécuté.
 */
public final class SandboxedBuildAnalyzer implements Analyzer {

    private static final Pattern SUREFIRE = Pattern.compile("Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+)");

    private final DockerSandbox sandbox;
    private final List<String> command;

    public SandboxedBuildAnalyzer(DockerSandbox sandbox, List<String> command) {
        this.sandbox = sandbox;
        this.command = command;
    }

    @Override
    public CriterionResult analyze(AnalysisContext ctx) {
        if (command.isEmpty()) {
            return CriterionResult.failed(ctx.criterion(), "aucune commande configurée (sandbox.command)", "bac à sable");
        }
        if (!sandbox.isAvailable()) {
            return CriterionResult.failed(ctx.criterion(),
                    "Docker indisponible : par sécurité, le projet n'est jamais exécuté sur la machine hôte", "bac à sable");
        }
        ctx.listener().progress(ctx.criterion(), "Exécution isolée dans Docker (" + sandbox.policy().image() + ")");
        try {
            DockerSandbox.ExecutionResult r = sandbox.run(ctx.project().rootPath(), command);
            ctx.trace().info("Bac à sable : code " + r.exitCode() + ", " + r.duration().toSeconds() + " s, timeout=" + r.timedOut());
            int run = -1;
            int failures = 0;
            Matcher m = SUREFIRE.matcher(r.output());
            while (m.find()) {
                run = Integer.parseInt(m.group(1));
                failures = Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3));
            }
            Checklist checklist = new Checklist()
                    .check(!r.timedOut(), 2, "Exécution terminée dans le temps imparti",
                            "Temps maximal dépassé (" + sandbox.policy().timeout().toSeconds() + " s)", "Vérifier les boucles infinies ou tests trop longs")
                    .check(r.exitCode() == 0, 4, "Build et tests réussis dans un environnement isolé",
                            "Échec du build ou des tests (code " + r.exitCode() + ")", "Faire passer 'mvn test' sur une machine vierge")
                    .check(run > 0, 2, run + " test(s) exécuté(s)", "Aucun test exécuté détecté", "Ajouter des tests exécutables")
                    .check(run > 0 && failures == 0, 2, "Aucun test en échec", failures + " test(s) en échec ou en erreur", "Corriger les tests en échec");
            if (r.timedOut()) {
                checklist.issue("Exécution interrompue après le délai maximal : conteneur supprimé");
            }
            return checklist.toResult(ctx.criterion(), "exécution isolée Docker (réseau "
                    + (sandbox.policy().networkEnabled() ? "activé" : "désactivé") + ")");
        } catch (IOException e) {
            return CriterionResult.failed(ctx.criterion(), e.getMessage(), "bac à sable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CriterionResult.failed(ctx.criterion(), "exécution interrompue", "bac à sable");
        }
    }
}
