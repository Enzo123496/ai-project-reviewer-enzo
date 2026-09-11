package fr.master.reviewer.analysis.deterministic;

import fr.master.reviewer.analysis.AnalysisContext;
import fr.master.reviewer.analysis.Analyzer;
import fr.master.reviewer.analysis.CriterionResult;
import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Analyse déterministe (sans LLM, instantanée, reproductible) de la présence et de la densité des tests. */
public final class TestPresenceAnalyzer implements Analyzer {

    private static final Pattern ASSERTION = Pattern.compile("\\b(assert\\w*|verify)\\s*\\(");
    private static final Pattern TEST_ANNOTATION = Pattern.compile("@(Test|ParameterizedTest|RepeatedTest)\\b");

    @Override
    public CriterionResult analyze(AnalysisContext ctx) {
        List<FileNode> files = ctx.project().files().toList();
        long sources = files.stream().filter(f -> f.type() == FileType.JAVA_SOURCE).count();
        List<FileNode> tests = files.stream().filter(f -> f.type() == FileType.TEST).toList();
        int testMethods = 0;
        int assertions = 0;
        for (FileNode t : tests) {
            String content = ctx.reader().read(t);
            testMethods += count(TEST_ANNOTATION, content);
            assertions += count(ASSERTION, content);
        }
        boolean framework = files.stream().filter(f -> f.type() == FileType.BUILD)
                .map(ctx.reader()::read).anyMatch(c -> c.contains("junit") || c.contains("testng"));
        double ratio = sources == 0 ? 0 : (double) tests.size() / sources;

        Checklist checklist = new Checklist()
                .check(!tests.isEmpty(), 2, tests.size() + " fichier(s) de test présents",
                        "Aucun fichier de test détecté", "Ajouter des tests unitaires (JUnit 5) dans src/test/java")
                .partial(ratio / 0.5, 3, String.format("Bon ratio fichiers de test / sources (%.2f)", ratio),
                        String.format("Ratio fichiers de test / sources faible (%.2f)", ratio),
                        "Viser au moins un fichier de test pour deux classes métier")
                .check(framework, 1, "Framework de test déclaré dans le build",
                        "Aucun framework de test déclaré dans pom.xml/build.gradle", "Déclarer JUnit dans le build")
                .partial(testMethods == 0 ? 0 : (double) assertions / testMethods / 2.0, 2,
                        "Tests avec plusieurs assertions (" + assertions + " pour " + testMethods + " test(s))",
                        "Peu d'assertions par test (" + assertions + " pour " + testMethods + " test(s))",
                        "Vérifier des résultats précis dans chaque test (assertEquals, assertThrows...)")
                .partial(sources == 0 ? 0 : (double) testMethods / (sources * 2.0), 2,
                        testMethods + " méthode(s) de test pour " + sources + " classe(s)",
                        "Trop peu de méthodes de test (" + testMethods + " pour " + sources + " classe(s))",
                        "Viser au moins deux cas de test par classe métier (cas nominal et cas d'erreur)");
        return checklist.toResult(ctx.criterion(), "déterministe (comptage des tests)");
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
