package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.Project;
import fr.master.reviewer.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSummarizerTest {

    private final StructureSummarizer summarizer = new StructureSummarizer();

    private static StructureSummarizer.Summary summarize(Path dir, Map<String, String> files, int budget) {
        Project project = TestData.load(TestData.writeProject(dir, files));
        List<FileNode> all = project.files().toList();
        return new StructureSummarizer().summarize(project, all, ContentReader.fromDisk(), budget);
    }

    @Test
    void capturesSignaturesThatTheFirstVersionMissed(@TempDir Path dir) {
        String text = summarize(dir, Map.of("src/main/java/bank/Account.java", String.join("\n",
                "package bank;",
                "import java.util.*;",
                "public class Account {",
                "    public Account(String id) { }",
                "    public void setBalance(double b) { balance = b; }",
                "    public Map<String, List<Account>> groups() { return null; }",
                "    public void transfer(String from,",
                "                         String to,",
                "                         double amount) {",
                "    }",
                "}")), 10_000).text();

        assertTrue(text.contains("ctor: public Account(String id)"), text);
        assertTrue(text.contains("public void setBalance(double b)"), "méthode sur une ligne contenant '='");
        assertTrue(text.contains("public Map<String, List<Account>> groups()"), "générique avec espaces");
        assertTrue(text.contains("public void transfer(String from, String to, double amount)"), "signature sur 3 lignes");
    }

    @Test
    void listsInterfaceMethodsWithoutPublicKeyword(@TempDir Path dir) {
        String text = summarize(dir, Map.of("src/main/java/bank/Repository.java", String.join("\n",
                "package bank;",
                "public interface Repository {",
                "    Account find(String id);",
                "    default int count() { return 0; }",
                "}")), 10_000).text();

        assertTrue(text.contains("Account find(String id)"), text);
        assertTrue(text.contains("default int count()"), text);
    }

    @Test
    void findsSamePackageAndImportedDependenciesButIgnoresCommentsAndStrings(@TempDir Path dir) {
        String text = summarize(dir, Map.of(
                "src/main/java/bank/Account.java", "package bank;\npublic class Account {}",
                "src/main/java/bank/Ghost.java", "package bank;\npublic class Ghost {}",
                "src/main/java/bank/core/Ledger.java", "package bank.core;\npublic class Ledger {}",
                "src/main/java/bank/BankService.java", String.join("\n",
                        "package bank;",
                        "import bank.core.Ledger;",
                        "public class BankService {",
                        "    private Account account;       // Ghost n'est cité qu'ici",
                        "    private Ledger ledger;",
                        "    private String label = \"Ghost\";",
                        "}")), 10_000).text();

        String line = text.lines().filter(l -> l.contains("depends on") && text.indexOf(l) > text.indexOf("BankService.java"))
                .findFirst().orElseThrow();
        assertTrue(line.contains("Account") && line.contains("Ledger"), line);
        assertFalse(line.contains("Ghost"), "commentaire et chaîne ne créent pas de dépendance");
    }

    @Test
    void reportsPackageDependencyCycles(@TempDir Path dir) {
        String text = summarize(dir, Map.of(
                "src/main/java/app/ui/Screen.java", "package app.ui;\nimport app.core.Engine;\npublic class Screen { Engine e; }",
                "src/main/java/app/core/Engine.java", "package app.core;\nimport app.ui.Screen;\npublic class Engine { Screen s; }"), 10_000).text();

        assertTrue(text.contains("core -> ui"), text);
        assertTrue(text.contains("cycles: core <-> ui"), text);
    }

    @Test
    void degradesDetailLevelBeforeTruncating(@TempDir Path dir) {
        Map<String, String> files = new java.util.HashMap<>();
        for (int i = 0; i < 30; i++) {
            files.put("src/main/java/p/C" + i + ".java", "package p;\npublic class C" + i + " {\n"
                    + "    public void methodeAvecUnNomAssezLong" + i + "(String premierParametre, String second) { }\n".repeat(1) + "}");
        }
        StructureSummarizer.Summary full = summarize(dir, files, 100_000);
        StructureSummarizer.Summary reduced = new StructureSummarizer().summarize(TestData.load(dir),
                TestData.load(dir).files().toList(), ContentReader.fromDisk(), full.text().length() - 1);
        StructureSummarizer.Summary tiny = new StructureSummarizer().summarize(TestData.load(dir),
                TestData.load(dir).files().toList(), ContentReader.fromDisk(), 300);

        assertEquals(StructureSummarizer.DetailLevel.FULL, full.level());
        assertTrue(reduced.level() != StructureSummarizer.DetailLevel.FULL && !reduced.truncated(),
                "on retire les méthodes plutôt que de couper des fichiers");
        assertTrue(reduced.text().contains("C29"), "tous les fichiers restent présents");
        assertTrue(tiny.truncated());
    }
}
