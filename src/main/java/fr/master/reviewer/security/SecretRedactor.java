package fr.master.reviewer.security;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Masque les secrets dans un texte : utilisé pour les logs/la trace (ne jamais écrire une clé d'API)
 * et pour le contenu envoyé à un LLM distant (ne pas divulguer les mots de passe du projet analysé).
 * Le NOM du secret est conservé ("password = ***") : le LLM voit qu'un secret était codé en dur.
 */
public final class SecretRedactor {

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
                    "[CLE PRIVEE MASQUEE]"),
            new Rule(Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1***"),
            new Rule(Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}"), "Bearer ***"),
            new Rule(Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}"), "sk-***"),
            new Rule(Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"), "AKIA***"),
            new Rule(Pattern.compile("(?i)(\"?[\\w.-]*(?:api[_-]?key|apikey|access[_-]?token|auth[_-]?token|secret|password|passwd|pwd)[\\w.-]*\"?\\s*[:=]\\s*)(\"[^\"\\n]*\"|'[^'\\n]*'|[^\\s,;]+)"),
                    "$1***"));

    private final Set<String> knownSecrets = ConcurrentHashMap.newKeySet();

    /** Enregistre une valeur exacte à masquer partout (ex. la clé d'API lue dans l'environnement). */
    public void registerSecret(String secret) {
        if (secret != null && secret.length() >= 6) {
            knownSecrets.add(secret);
        }
    }

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (String secret : knownSecrets) {
            result = result.replace(secret, "***");
        }
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }
}
