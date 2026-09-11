package fr.master.reviewer.report;

/**
 * Échappement LaTeX. INDISPENSABLE : le texte des rapports vient en partie du LLM et du projet analysé
 * (données non fiables). Sans échappement, un commentaire contenant \input{/etc/passwd} ou \write18
 * deviendrait une commande LaTeX. Ici tout caractère spécial est neutralisé.
 */
public final class LatexEscaper {

    private LatexEscaper() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        text.codePoints().forEach(cp -> {
            switch (cp) {
                case '\\' -> sb.append("\\textbackslash{}");
                case '{' -> sb.append("\\{");
                case '}' -> sb.append("\\}");
                case '$' -> sb.append("\\$");
                case '&' -> sb.append("\\&");
                case '#' -> sb.append("\\#");
                case '_' -> sb.append("\\_");
                case '%' -> sb.append("\\%");
                case '~' -> sb.append("\\textasciitilde{}");
                case '^' -> sb.append("\\textasciicircum{}");
                case '<' -> sb.append("\\textless{}");
                case '>' -> sb.append("\\textgreater{}");
                case '|' -> sb.append("\\textbar{}");
                // Un texte commençant par [ serait pris pour l'argument optionnel de \item : on protège les crochets.
                case '[' -> sb.append("{[}");
                case ']' -> sb.append("{]}");
                case '/' -> sb.append("/\\allowbreak{}"); // autorise la coupure des longs chemins
                case '\n', '\r', '\t' -> sb.append(' ');
                case 0x2019 -> sb.append('\'');
                case 0x2018 -> sb.append('`');
                case 0x201C -> sb.append("``");
                case 0x201D -> sb.append("''");
                case 0x2013 -> sb.append("--");
                case 0x2014 -> sb.append("---");
                case 0x2026 -> sb.append("\\ldots{}");
                case 0x20AC -> sb.append("\\texteuro{}");
                case 0x2022 -> sb.append("\\textbullet{}");
                case 0x00A0 -> sb.append('~');
                default -> {
                    if (cp < 0x20) {
                        // caractère de contrôle : supprimé
                    } else if (cp <= 0x17F) {
                        sb.appendCodePoint(cp); // ASCII + Latin-1 + Latin étendu A (accents français)
                    } else {
                        sb.append('?'); // emoji, idéogrammes... non supportés par pdflatex
                    }
                }
            }
        });
        return sb.toString();
    }
}
