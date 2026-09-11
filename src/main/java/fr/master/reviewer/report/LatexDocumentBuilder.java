package fr.master.reviewer.report;

import java.util.List;
import java.util.Map;

/**
 * PATTERN BUILDER : construit un document LaTeX morceau par morceau.
 * Sécurité "par défaut" : toutes les méthodes publiques ÉCHAPPENT le texte reçu. Seules les cellules
 * créées avec Cell.raw() (réservé au générateur, pour les barres de score) sont insérées telles quelles.
 */
public final class LatexDocumentBuilder {

    /** Cellule de tableau : texte échappé, ou LaTeX brut produit par notre propre code. */
    public record Cell(String value, boolean raw) {
        public static Cell text(String value) {
            return new Cell(value, false);
        }

        static Cell raw(String latex) {
            return new Cell(latex, true);
        }

        String render() {
            return raw ? value : LatexEscaper.escape(value);
        }
    }

    private final StringBuilder body = new StringBuilder();
    private String title = "";
    private String subtitle = "";
    private String date = "";

    public LatexDocumentBuilder title(String title, String subtitle, String date) {
        this.title = title;
        this.subtitle = subtitle;
        this.date = date;
        return this;
    }

    public LatexDocumentBuilder section(String name) {
        body.append("\\section{").append(LatexEscaper.escape(name)).append("}\n");
        return this;
    }

    public LatexDocumentBuilder subsection(String name) {
        body.append("\\subsection{").append(LatexEscaper.escape(name)).append("}\n");
        return this;
    }

    public LatexDocumentBuilder paragraph(String text) {
        body.append(LatexEscaper.escape(text)).append("\n\n");
        return this;
    }

    public LatexDocumentBuilder emphasis(String label, String text) {
        body.append("\\noindent\\textbf{").append(LatexEscaper.escape(label)).append("} ")
                .append(LatexEscaper.escape(text)).append("\n\n");
        return this;
    }

    public LatexDocumentBuilder bigScore(String label, String score) {
        body.append("\\begin{center}\\Large\\textbf{").append(LatexEscaper.escape(label)).append(" ")
                .append(LatexEscaper.escape(score)).append("}\\end{center}\n");
        return this;
    }

    public LatexDocumentBuilder list(String label, List<String> items) {
        if (items.isEmpty()) {
            return this;
        }
        body.append("\\paragraph{").append(LatexEscaper.escape(label)).append("}\n\\begin{itemize}\n");
        items.forEach(i -> body.append("  \\item ").append(LatexEscaper.escape(i)).append('\n'));
        body.append("\\end{itemize}\n");
        return this;
    }

    public LatexDocumentBuilder smallList(String label, List<String> items) {
        if (items.isEmpty()) {
            return this;
        }
        body.append("\\paragraph{").append(LatexEscaper.escape(label)).append("}\n{\\footnotesize\\begin{itemize}\n");
        items.forEach(i -> body.append("  \\item ").append(LatexEscaper.escape(i)).append('\n'));
        body.append("\\end{itemize}}\n");
        return this;
    }

    public LatexDocumentBuilder keyValueTable(Map<String, String> rows) {
        body.append("\\begin{tabularx}{\\textwidth}{@{}>{\\raggedright\\bfseries}p{5.6cm}>{\\raggedright\\arraybackslash}X@{}}\n\\toprule\n");
        rows.forEach((k, v) -> body.append(LatexEscaper.escape(k)).append(" & ").append(LatexEscaper.escape(v)).append(" \\\\\n"));
        body.append("\\bottomrule\n\\end{tabularx}\n\n");
        return this;
    }

    public LatexDocumentBuilder table(List<String> headers, String columnSpec, List<List<Cell>> rows) {
        body.append("\\begin{longtable}{").append(columnSpec).append("}\n\\toprule\n");
        body.append(String.join(" & ", headers.stream().map(h -> "\\textbf{" + LatexEscaper.escape(h) + "}").toList()))
                .append(" \\\\\n\\midrule\n\\endhead\n");
        for (List<Cell> row : rows) {
            body.append(String.join(" & ", row.stream().map(Cell::render).toList())).append(" \\\\\n");
        }
        body.append("\\bottomrule\n\\end{longtable}\n\n");
        return this;
    }

    public String build() {
        return """
                %% Rapport généré automatiquement par AI Project Reviewer. Ne pas modifier à la main.
                \\documentclass[11pt,a4paper]{article}
                \\usepackage[utf8]{inputenc}
                \\usepackage[T1]{fontenc}
                \\usepackage{textcomp}
                \\IfFileExists{lmodern.sty}{\\usepackage{lmodern}}{}
                \\setlength{\\emergencystretch}{3em}
                \\usepackage[margin=2.2cm]{geometry}
                \\usepackage{booktabs,longtable,tabularx,array}
                \\usepackage{xcolor}
                \\usepackage{enumitem}
                \\usepackage[hidelinks]{hyperref}
                \\setlist{nosep,leftmargin=1.5em}
                \\definecolor{scoregood}{HTML}{2E7D32}
                \\definecolor{scoremid}{HTML}{EF8F00}
                \\definecolor{scorelow}{HTML}{C62828}
                \\setlength{\\parindent}{0pt}
                \\setlength{\\parskip}{4pt}
                \\title{%s\\\\\\large %s}
                \\author{AI Project Reviewer}
                \\date{%s}
                \\begin{document}
                \\maketitle
                \\tableofcontents
                \\newpage
                %s
                \\end{document}
                """.formatted(LatexEscaper.escape(title), LatexEscaper.escape(subtitle), LatexEscaper.escape(date), body);
    }
}
