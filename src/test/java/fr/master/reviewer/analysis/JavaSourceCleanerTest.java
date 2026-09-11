package fr.master.reviewer.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceCleanerTest {

    @Test
    void removesCommentsAndLiteralsButKeepsCodeAndLineStructure() {
        String source = String.join("\n",
                "class A { // Ghost dans un commentaire",
                "  String s = \"Ghost \\\" toujours dans la chaîne\"; char q = '\"';",
                "  /* bloc",
                "     Ghost */ int x;",
                "  String t = \"\"\"",
                "      Ghost dans un bloc de texte",
                "      \"\"\";",
                "  Account account; String url = \"http://exemple\";",
                "}");

        String cleaned = JavaSourceCleaner.stripCommentsAndLiterals(source);

        assertFalse(cleaned.contains("Ghost"), cleaned);
        assertTrue(cleaned.contains("int x;"));
        assertTrue(cleaned.contains("Account account;"), "le code après une chaîne contenant // est conservé");
        assertEquals(source.lines().count(), cleaned.lines().count(), "les numéros de ligne restent valables");
    }
}
