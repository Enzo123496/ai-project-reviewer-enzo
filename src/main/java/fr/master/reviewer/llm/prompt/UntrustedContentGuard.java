package fr.master.reviewer.llm.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Défenses contre l'INJECTION DE PROMPT. Le code analysé est une donnée non fiable :
 * 1. il est encadré par des balises contenant un identifiant ALÉATOIRE (nonce) que l'auteur du code ne
 *    peut pas deviner, donc il ne peut pas "fermer" la zone de données pour écrire des instructions ;
 * 2. les phrases typiques d'injection sont DÉTECTÉES et remontées dans le rapport (sans rien exécuter).
 * Ces mesures RÉDUISENT le risque sans l'éliminer : d'où la validation stricte des réponses ensuite.
 */
public final class UntrustedContentGuard {

    private static final List<Pattern> SUSPICIOUS = List.of(
            Pattern.compile("(?i)\\b(ignore|disregard|forget)\\b.{0,20}\\b(previous|prior|above|earlier|all)\\b.{0,20}\\b(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)\\b(give|assign|rate|grade)\\b.{0,40}\\b(10\\s*/\\s*10|20\\s*/\\s*20|full marks|maximum score|perfect score)"),
            Pattern.compile("(?i)\\byou are now\\b|\\bnew instructions?\\s*:|\\bsystem prompt\\b"),
            Pattern.compile("(?i)\\bignor\\w*\\b.{0,20}\\binstructions?\\b.{0,20}\\b(pr[ée]c[ée]dentes?|ci-dessus)"),
            Pattern.compile("(?i)\\b(donn\\w*|attribu\\w*|mett\\w*|mets)\\b.{0,40}\\b(note|score)\\b.{0,20}(10\\s*/\\s*10|20\\s*/\\s*20|maximale)"),
            Pattern.compile("(?i)</?\\s*(system|assistant|instructions?)\\s*>|\\[/?INST]|<\\|im_(start|end)\\|>"),
            Pattern.compile("(?i)untrusted-data"));

    private static final int MAX_DETECTIONS = 10;

    private final Supplier<String> fixedNonce;
    private final byte[] sessionSecret = new byte[32];

    /**
     * Le nonce est dérivé du CONTENU et d'un secret tiré au démarrage : imprévisible pour l'auteur du code
     * (il ne connaît pas le secret) mais identique pour un même contenu pendant la session.
     * Un nonce purement aléatoire à chaque appel rendait le cache inutile (prompt toujours différent).
     */
    public UntrustedContentGuard() {
        new SecureRandom().nextBytes(sessionSecret);
        this.fixedNonce = null;
    }

    /** Constructeur de test : nonce prévisible. */
    public UntrustedContentGuard(Supplier<String> fixedNonce) {
        this.fixedNonce = fixedNonce;
    }

    public String nonceFor(String content) {
        if (fixedNonce != null) {
            return fixedNonce.get();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sessionSecret);
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Encadre le contenu. Toute occurrence du mot-clé de balise dans le contenu est neutralisée. */
    public String wrap(String content, String nonce) {
        String neutralized = content.replaceAll("(?i)untrusted-data", "untrusted_data");
        return "<untrusted-data id=\"" + nonce + "\">\n" + neutralized + "\n</untrusted-data id=\"" + nonce + "\">";
    }

    /** Retourne les lignes suspectes sous la forme "fichier:ligne : extrait". */
    public List<String> detectInjections(String fileLabel, String content) {
        List<String> detections = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length && detections.size() < MAX_DETECTIONS; i++) {
            String line = lines[i];
            for (Pattern p : SUSPICIOUS) {
                if (p.matcher(line).find()) {
                    String excerpt = line.strip();
                    if (excerpt.length() > 120) {
                        excerpt = excerpt.substring(0, 120) + "...";
                    }
                    detections.add(fileLabel + ":" + (i + 1) + " : " + excerpt);
                    break;
                }
            }
        }
        return detections;
    }
}
