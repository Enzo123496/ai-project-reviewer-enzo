package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileNode;
import fr.master.reviewer.project.FileType;
import fr.master.reviewer.project.Project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RÉSUMÉ INTERMÉDIAIRE DÉTERMINISTE pour juger l'architecture sans envoyer le corps des méthodes.
 *
 * Contenu, du plus important au plus détaillé :
 *   1. graphe des dépendances ENTRE PACKAGES et cycles détectés (couplage) ;
 *   2. arborescence compacte (dossiers et nombre de fichiers par type) ;
 *   3. pour chaque fichier Java : types déclarés, dépendances internes, puis constructeurs et méthodes publiques.
 *
 * DÉGRADATION PAR NIVEAUX : si le résumé complet dépasse le budget, on retire d'abord les méthodes
 * (niveau "types et dépendances"), puis on passe à une ligne par fichier (niveau "compact"), et on ne
 * tronque qu'en dernier recours. Mesuré sur notre projet : la troncature brute perdait 41 fichiers sur 83.
 */
public final class StructureSummarizer {

    /** Niveau de détail finalement utilisé. */
    public enum DetailLevel {
        FULL("complet"), TYPES_AND_DEPENDENCIES("types et dépendances"), COMPACT("compact");

        private final String label;

        DetailLevel(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Summary(String text, boolean truncated, DetailLevel level) {
    }

    /** Ce qu'on a extrait d'un fichier Java. */
    record JavaFileInfo(String path, String packageName, List<String> typeDeclarations, Set<String> typeNames,
                        List<String> constructors, List<String> methods, Set<String> dependencies, long lines) {
    }

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;");
    private static final Pattern TYPE = Pattern.compile(
            "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\\s+)*"
                    + "(class|interface|enum|record|@interface)\\s+(\\w+)");
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:public|protected)\\s+(?:(?:static|final|abstract|synchronized|default|native)\\s+)*"
                    + "(?:<[^>]+>\\s+)?[\\w.$]+(?:\\s*<.*>)?(?:\\[\\])*\\s+(\\w+)\\s*\\([^)]*\\)");
    private static final Pattern CONSTRUCTOR = Pattern.compile("^\\s*(?:public|protected)\\s+([A-Z]\\w*)\\s*\\([^)]*\\)");
    private static final Pattern INTERFACE_METHOD = Pattern.compile(
            "^\\s*(?:default\\s+|static\\s+)?(?:<[^>]+>\\s+)?[\\w.$]+(?:\\s*<.*>)?(?:\\[\\])*\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?[;{]");
    /** Début de déclaration dont la parenthèse ne se ferme pas sur la même ligne. */
    private static final Pattern OPEN_SIGNATURE = Pattern.compile(
            "^\\s*(?:@\\w+\\s+)*(?:public|protected|default|static|abstract|record)\\b[^;={]*\\([^)]*$");
    private static final Pattern CAPITALIZED = Pattern.compile("\\b[A-Z]\\w*\\b");
    private static final Set<String> KEYWORDS = Set.of("return", "new", "throw", "else", "if", "for", "while", "switch", "case");
    private static final int MAX_METHODS_PER_FILE = 12;
    private static final int MAX_DIRECTORIES = 60;

    public Summary summarize(Project project, List<FileNode> files, ContentReader reader, int maxChars) {
        List<JavaFileInfo> infos = analyze(files, reader);
        String header = header(project, infos);
        for (DetailLevel level : DetailLevel.values()) {
            String text = header + body(infos, level);
            if (text.length() <= maxChars) {
                return new Summary(text, false, level);
            }
        }
        String compact = header + body(infos, DetailLevel.COMPACT);
        return new Summary(compact.substring(0, Math.max(0, maxChars)) + "\n... (summary truncated)", true, DetailLevel.COMPACT);
    }

    // ------------------------------------------------------------------ extraction

    List<JavaFileInfo> analyze(List<FileNode> files, ContentReader reader) {
        record Raw(FileNode file, String cleaned, String pkg, Set<String> names) {
        }
        List<Raw> raws = new ArrayList<>();
        for (FileNode f : files) {
            if (f.type() != FileType.JAVA_SOURCE && f.type() != FileType.TEST) {
                continue;
            }
            String cleaned = JavaSourceCleaner.stripCommentsAndLiterals(reader.read(f));
            String pkg = cleaned.lines().map(PACKAGE::matcher).filter(Matcher::find).map(m -> m.group(1)).findFirst().orElse("");
            Set<String> names = new LinkedHashSet<>();
            cleaned.lines().map(TYPE::matcher).filter(Matcher::find).forEach(m -> names.add(m.group(2)));
            raws.add(new Raw(f, cleaned, pkg, names));
        }
        // Index : nom simple de type -> packages où il est déclaré dans le projet
        Map<String, Set<String>> declared = new TreeMap<>();
        for (Raw r : raws) {
            r.names().forEach(n -> declared.computeIfAbsent(n, k -> new TreeSet<>()).add(r.pkg()));
        }

        List<JavaFileInfo> infos = new ArrayList<>();
        for (Raw r : raws) {
            infos.add(describe(r.file(), r.cleaned(), r.pkg(), r.names(), declared));
        }
        return infos;
    }

    private JavaFileInfo describe(FileNode file, String cleaned, String pkg, Set<String> ownNames,
                                  Map<String, Set<String>> declared) {
        List<String> types = new ArrayList<>();
        List<String> constructors = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        Set<String> explicitImports = new LinkedHashSet<>();
        Set<String> wildcardPackages = new LinkedHashSet<>();
        String currentKind = "";

        List<String> lines = joinMultiLineSignatures(cleaned.lines().toList());
        for (String line : lines) {
            Matcher im = IMPORT.matcher(line);
            Matcher tm = TYPE.matcher(line);
            if (im.find()) {
                if (im.group(3) != null) {
                    wildcardPackages.add(im.group(2));
                } else {
                    explicitImports.add(im.group(2));
                }
            } else if (tm.find()) {
                currentKind = tm.group(1);
                types.add(shorten(line.strip().replaceAll("\\s*\\{.*$", "")));
            } else if (methods.size() + constructors.size() < MAX_METHODS_PER_FILE) {
                Matcher cm = CONSTRUCTOR.matcher(line);
                if (cm.find() && ownNames.contains(cm.group(1))) {
                    constructors.add(shorten(signature(line)));
                } else if (METHOD.matcher(line).find()
                        || ("interface".equals(currentKind) && INTERFACE_METHOD.matcher(line).find()
                        && KEYWORDS.stream().noneMatch(k -> line.strip().startsWith(k + " ")))) {
                    methods.add(shorten(signature(line)));
                }
            }
        }

        // Dépendances internes : identifiants en majuscule utilisés dans le code, déclarés dans le projet,
        // et réellement accessibles (même package, import explicite ou import générique).
        Set<String> dependencies = new LinkedHashSet<>();
        Matcher words = CAPITALIZED.matcher(cleaned);
        while (words.find()) {
            String name = words.group();
            Set<String> packages = declared.get(name);
            if (packages == null || ownNames.contains(name)) {
                continue;
            }
            boolean samePackage = packages.contains(pkg);
            boolean imported = packages.stream().anyMatch(p -> explicitImports.contains(p + "." + name))
                    || packages.stream().anyMatch(wildcardPackages::contains);
            if (samePackage || imported) {
                dependencies.add(name);
            }
        }
        return new JavaFileInfo(file.relativePath(), pkg, types, ownNames, constructors, methods, dependencies,
                cleaned.lines().count());
    }

    /** Recolle les signatures écrites sur plusieurs lignes (paramètres nombreux, records). */
    static List<String> joinMultiLineSignatures(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (OPEN_SIGNATURE.matcher(line).find()) {
                StringBuilder joined = new StringBuilder(line.strip());
                int j = i + 1;
                while (j < lines.size() && j <= i + 6 && joined.indexOf(")") < 0) {
                    joined.append(' ').append(lines.get(j).strip());
                    j++;
                }
                if (joined.indexOf(")") >= 0) {
                    result.add(joined.toString());
                    i = j - 1;
                    continue;
                }
            }
            result.add(line);
        }
        return result;
    }

    private static String signature(String line) {
        return line.strip().replaceAll("\\s*(\\{.*|;)$", "").replaceAll("\\s+", " ");
    }

    private static String shorten(String s) {
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }

    // ------------------------------------------------------------------ mise en forme

    private String header(Project project, List<JavaFileInfo> infos) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT: ").append(project.files().count()).append(" files, ").append(infos.size())
                .append(" Java files analysed\n\n");
        sb.append(packageGraph(infos)).append('\n');
        sb.append("DIRECTORIES (file count by type)\n");
        Map<String, Map<FileType, Long>> byDirectory = new TreeMap<>();
        project.files().forEach(f -> byDirectory.computeIfAbsent(FileInterleaver.directoryOf(f).isEmpty() ? "." : FileInterleaver.directoryOf(f),
                d -> new TreeMap<>()).merge(f.type(), 1L, Long::sum));
        byDirectory.entrySet().stream().limit(MAX_DIRECTORIES).forEach(e -> sb.append("  ").append(e.getKey()).append("/ : ")
                .append(e.getValue().entrySet().stream().map(t -> t.getValue() + " " + t.getKey()).collect(Collectors.joining(", ")))
                .append('\n'));
        if (byDirectory.size() > MAX_DIRECTORIES) {
            sb.append("  ... (+").append(byDirectory.size() - MAX_DIRECTORIES).append(" directories)\n");
        }
        return sb.append('\n').toString();
    }

    /** Dépendances entre packages (couplage à gros grain) et cycles directs A <-> B. */
    String packageGraph(List<JavaFileInfo> infos) {
        Map<String, String> packageOfType = new LinkedHashMap<>();
        infos.forEach(i -> i.typeNames().forEach(t -> packageOfType.putIfAbsent(t, i.packageName())));
        String prefix = commonPrefix(infos.stream().map(JavaFileInfo::packageName).filter(p -> !p.isEmpty()).distinct().toList());
        Function<String, String> shortName = p -> p.isEmpty() ? "(default)"
                : prefix.isEmpty() ? p : p.equals(prefix) ? "(root)" : p.substring(prefix.length() + 1);

        Map<String, Set<String>> graph = new TreeMap<>();
        for (JavaFileInfo info : infos) {
            Set<String> targets = graph.computeIfAbsent(shortName.apply(info.packageName()), k -> new TreeSet<>());
            for (String dep : info.dependencies()) {
                String target = packageOfType.get(dep);
                if (target != null && !target.equals(info.packageName())) {
                    targets.add(shortName.apply(target));
                }
            }
        }
        StringBuilder sb = new StringBuilder("PACKAGE DEPENDENCIES").append(prefix.isEmpty() ? "" : " (relative to " + prefix + ")").append('\n');
        graph.forEach((from, to) -> sb.append("  ").append(from).append(" -> ").append(to.isEmpty() ? "(none)" : String.join(", ", to)).append('\n'));
        Set<String> cycles = new TreeSet<>();
        graph.forEach((a, targets) -> targets.forEach(b -> {
            if (graph.getOrDefault(b, Set.of()).contains(a)) {
                cycles.add(a.compareTo(b) < 0 ? a + " <-> " + b : b + " <-> " + a);
            }
        }));
        sb.append("  cycles: ").append(cycles.isEmpty() ? "none" : String.join("; ", cycles)).append('\n');
        return sb.toString();
    }

    private String body(List<JavaFileInfo> infos, DetailLevel level) {
        StringBuilder sb = new StringBuilder("JAVA FILES (level: ").append(level.name()).append(")\n");
        for (JavaFileInfo i : infos) {
            String deps = i.dependencies().isEmpty() ? "" : String.join(", ", i.dependencies());
            if (level == DetailLevel.COMPACT) {
                sb.append(i.path()).append(" | ").append(String.join("; ", i.typeNames()))
                        .append(deps.isEmpty() ? "" : " | depends on: " + deps).append('\n');
                continue;
            }
            sb.append("\n# ").append(i.path()).append("  (").append(i.lines()).append(" lines)\n");
            i.typeDeclarations().forEach(t -> sb.append("  type: ").append(t).append('\n'));
            if (level == DetailLevel.FULL) {
                i.constructors().forEach(c -> sb.append("    ctor: ").append(c).append('\n'));
                i.methods().forEach(m -> sb.append("    ").append(m).append('\n'));
            }
            if (!deps.isEmpty()) {
                sb.append("  depends on: ").append(deps).append('\n');
            }
        }
        return sb.toString();
    }

    private static String commonPrefix(List<String> packages) {
        if (packages.size() < 2) {
            return "";
        }
        String[] first = packages.get(0).split("\\.");
        int common = first.length;
        for (String p : packages) {
            String[] parts = p.split("\\.");
            int k = 0;
            while (k < Math.min(common, parts.length) && parts[k].equals(first[k])) {
                k++;
            }
            common = k;
        }
        return String.join(".", java.util.Arrays.copyOf(first, common));
    }
}
