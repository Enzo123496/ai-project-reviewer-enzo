package fr.master.reviewer.analysis;

/**
 * Retire commentaires et contenus littéraux (chaînes, caractères, blocs de texte) d'un source Java,
 * en CONSERVANT les retours à la ligne pour que la structure ligne par ligne reste exploitable.
 * Sans ce nettoyage, un nom de classe dans un commentaire ou une chaîne créerait une fausse dépendance,
 * et "class" dans une chaîne ferait croire à une déclaration.
 *
 * C'est une MACHINE À ÉTATS (même principe que l'extraction JSON) : on lit caractère par caractère
 * en sachant si l'on est dans du code, un commentaire ou une chaîne.
 */
public final class JavaSourceCleaner {

    private JavaSourceCleaner() {
    }

    public static String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int n = source.length();
        int i = 0;
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {                       // commentaire de ligne
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {                // commentaire de bloc (et Javadoc)
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else if (source.startsWith("\"\"\"", i)) {         // bloc de texte Java 15+
                out.append("\"\"");
                i += 3;
                while (i < n && !source.startsWith("\"\"\"", i)) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                i += 3;
            } else if (c == '"' || c == '\'') {                  // chaîne ou caractère
                out.append(c).append(c);
                i++;
                while (i < n && source.charAt(i) != c && source.charAt(i) != '\n') {
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
