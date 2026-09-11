package fr.master.reviewer.security;

import java.time.Duration;

/**
 * Restrictions appliquées au conteneur (principe du MOINDRE PRIVILÈGE).
 * Par défaut : pas de réseau, système de fichiers racine en lecture seule, utilisateur non-root,
 * aucune capacité Linux, CPU/mémoire/processus limités, durée maximale.
 */
public record SandboxPolicy(String image, String memory, String cpus, int pidsLimit, Duration timeout,
                            boolean networkEnabled, String user, String tmpfsSize) {

    public SandboxPolicy {
        if (image == null || !image.matches("[a-z0-9][a-z0-9._/:@-]*")) {
            throw new IllegalArgumentException("Nom d'image Docker invalide : " + image);
        }
        if (memory == null || !memory.matches("\\d+[kmg]")) {
            throw new IllegalArgumentException("Limite mémoire invalide : " + memory);
        }
        if (cpus == null || !cpus.matches("\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("Limite CPU invalide : " + cpus);
        }
        if (user == null || !user.matches("\\d+:\\d+")) {
            throw new IllegalArgumentException("L'utilisateur doit être numérique uid:gid (non-root)");
        }
        if (user.startsWith("0:")) {
            throw new IllegalArgumentException("Exécution en root interdite dans le bac à sable");
        }
    }

    public static SandboxPolicy strict(String image, Duration timeout) {
        return new SandboxPolicy(image, "1g", "1.0", 256, timeout, false, "10001:10001", "512m");
    }
}
