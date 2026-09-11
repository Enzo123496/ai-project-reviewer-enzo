package fr.master.reviewer.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Détermine le type d'un fichier à partir de son chemin relatif, avec une liste ordonnée de règles
 * (la première qui correspond gagne). Supporter un nouveau langage = ajouter des règles.
 */
public final class FileClassifier {

    private record Rule(Predicate<String> matches, FileType type) {
    }

    private final List<Rule> rules = new ArrayList<>();

    public static FileClassifier defaultClassifier() {
        FileClassifier c = new FileClassifier();
        c.addRule(p -> endsWithAny(p, ".java") && isTestPath(p), FileType.TEST);
        c.addRule(p -> endsWithAny(p, ".java"), FileType.JAVA_SOURCE);
        c.addRule(p -> nameIn(p, "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
                "settings.gradle.kts", "gradlew", "gradlew.bat", "mvnw", "mvnw.cmd"), FileType.BUILD);
        c.addRule(p -> fileName(p).startsWith("dockerfile") || fileName(p).startsWith("docker-compose")
                || fileName(p).startsWith("compose.y") || nameIn(p, ".dockerignore"), FileType.DOCKER);
        c.addRule(p -> endsWithAny(p, ".md", ".txt", ".adoc", ".rst", ".tex")
                || fileName(p).startsWith("license") || p.startsWith("docs/"), FileType.DOCUMENTATION);
        c.addRule(p -> endsWithAny(p, ".sh", ".bat", ".cmd", ".ps1", ".py"), FileType.SCRIPT);
        c.addRule(p -> p.contains("src/main/resources/") || p.contains("src/test/resources/"), FileType.RESOURCE);
        c.addRule(p -> endsWithAny(p, ".properties", ".yml", ".yaml", ".xml", ".json", ".toml", ".ini",
                ".conf", ".cfg", ".env"), FileType.CONFIG);
        c.addRule(p -> endsWithAny(p, ".png", ".jpg", ".jpeg", ".gif", ".svg", ".css", ".html", ".fxml"),
                FileType.RESOURCE);
        return c;
    }

    public void addRule(Predicate<String> matches, FileType type) {
        rules.add(new Rule(matches, type));
    }

    public FileType classify(String relativePath) {
        String p = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (Rule rule : rules) {
            if (rule.matches().test(p)) {
                return rule.type();
            }
        }
        return FileType.OTHER;
    }

    private static boolean isTestPath(String p) {
        String name = fileName(p);
        return p.contains("src/test/") || p.startsWith("test/") || p.contains("/test/")
                || name.endsWith("test.java") || name.endsWith("tests.java") || name.endsWith("it.java");
    }

    private static String fileName(String p) {
        int i = p.lastIndexOf('/');
        return i < 0 ? p : p.substring(i + 1);
    }

    private static boolean nameIn(String p, String... names) {
        String name = fileName(p);
        for (String n : names) {
            if (name.equals(n)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithAny(String p, String... suffixes) {
        for (String s : suffixes) {
            if (p.endsWith(s)) {
                return true;
            }
        }
        return false;
    }
}
