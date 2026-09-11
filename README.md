# AI Project Reviewer

Plateforme d'évaluation automatique de projets logiciels Java combinant analyses déterministes et modèles de langage (LLM), avec génération d'un rapport LaTeX.

> Projet de Master — Architecture logicielle et IA. Le cœur est en Java 17, sans framework : l'objectif est de rendre l'architecture visible et justifiable.

---

## 1. Prérequis

| Outil | Version | Obligatoire ? |
|---|---|---|
| JDK | 17 ou plus | oui |
| Maven | 3.8 ou plus | oui |
| LM Studio (ou Ollama) | récent | pour une vraie évaluation par LLM |
| Docker | 24 ou plus | pour l'exécution isolée et le PDF isolé |
| TeX Live (pdflatex) | toute | seulement pour compiler le PDF en mode `local` |

Aucune clé d'API n'est nécessaire pour un LLM local.

## 2. Compiler

```bash
mvn package            # compile, lance les 80 tests, produit target/ai-project-reviewer-1.0.0.jar
mvn test               # uniquement les tests (aucun LLM ni Docker requis)
```

## 3. Configurer le LLM

Toute la configuration est dans `config/reviewer.json`.

### Option A — LLM local avec LM Studio (recommandé)

1. Installer LM Studio, télécharger un modèle instruct (ex. *Mistral 7B Instruct*, *Llama 3.1 8B Instruct*, *Qwen2.5-Coder 7B*).
2. Onglet **Developer** → charger le modèle → **Start Server** (port 1234 par défaut).
3. Copier l'identifiant exact du modèle affiché par LM Studio dans `config/reviewer.json` :

```json
{ "name": "lmstudio", "type": "openai-compatible", "baseUrl": "http://localhost:1234/v1",
  "model": "IDENTIFIANT-DU-MODELE", "apiKeyEnv": null, "responseFormat": "none" }
```

4. Vérifier que `"active": "lmstudio"`.

Conseil : fixez la longueur de contexte du modèle à au moins 8 000 jetons dans LM Studio. Les segments envoyés font au plus `maxCharsPerChunk` = 12 000 caractères (≈ 3 000 jetons) plus le prompt.

### Option B — API distante (Mistral, DeepSeek)

La clé n'est **jamais** écrite dans un fichier du dépôt. La configuration contient seulement le **nom** de la variable d'environnement :

```bash
export MISTRAL_API_KEY="votre-cle"      # Linux / macOS
setx MISTRAL_API_KEY "votre-cle"        # Windows (nouveau terminal ensuite)
```

puis choisir `mistral-api` dans l'interface ou mettre `"active": "mistral-api"`. Une propriété `apiKey` écrite en dur dans la configuration est **refusée au démarrage**.

### Option C — Sans LLM (démo / développement)

Choisir le fournisseur `mock` : réponses simulées, notes **sans valeur d'évaluation**, clairement signalées dans le rapport.

## 4. Démarrer l'application

```bash
java -jar target/ai-project-reviewer-1.0.0.jar                       # interface graphique
java -jar target/ai-project-reviewer-1.0.0.jar --config autre.json   # autre configuration
```

La commande doit être lancée depuis la racine du dépôt (chemin relatif `config/reviewer.json`), sinon utiliser `--config`.

## 5. Charger un projet

Boutons **Ouvrir un dossier…**, **Ouvrir une archive .zip…** ou **Dépôt Git…** (HTTPS). L'arborescence apparaît à gauche avec le type de chaque fichier. Aucun code du projet n'est exécuté au chargement.

## 6. Lancer une analyse

1. Choisir un **profil** (`complet`, `rapide`, `sans-llm`, `complet-avec-execution`) et ajuster les critères cochés.
2. Choisir le **modèle**.
3. **Lancer l'analyse** : la progression, le journal et les erreurs s'affichent en direct.
4. Cliquer sur une ligne de résultat pour voir points forts, faiblesses, problèmes et recommandations.

Chaque analyse est enregistrée dans `~/.ai-project-reviewer/history/` (onglet **Historique**, double-clic pour rouvrir).

## 7. Produire le rapport

Choisir le format (`latex` ou `html`) puis **Générer le rapport**. Le fichier est écrit dans `reports/<projet>-<date>/evaluation.tex`.

Compilation PDF (champ `report.pdfMode`) :

| Mode | Effet |
|---|---|
| `none` | pas de PDF (défaut) — compiler soi-même : `pdflatex -no-shell-escape evaluation.tex` (2 fois) |
| `local` | pdflatex local, avec `-no-shell-escape` |
| `docker` | pdflatex dans le conteneur `reviewer-latex` (sans réseau, non-root) — recommandé |

## 8. Ligne de commande (sans interface)

```bash
java -jar target/ai-project-reviewer-1.0.0.jar --cli \
     --project examples/sample-bank-project \
     --profile complet --provider lmstudio --format latex [--pdf] [--no-commentary] [--verbose]
```

## 9. Exécution isolée (Docker dans une VM)

Le critère `build-and-tests` compile et exécute les tests du projet évalué. **Ne jamais l'activer hors d'une machine virtuelle dédiée.**

```bash
# dans la VM
./scripts/vm-setup-ubuntu.sh     # Docker en mode rootless
./scripts/build-images.sh        # images reviewer-sandbox et reviewer-latex
```

Puis utiliser le profil `complet-avec-execution`. Si Docker est absent, le critère échoue proprement : le projet n'est **jamais** exécuté sur l'hôte.

Restrictions appliquées au conteneur : `--network none`, `--read-only`, `--user 10001:10001`, `--cap-drop ALL`, `--security-opt no-new-privileges`, `--memory`, `--cpus`, `--pids-limit`, projet monté en lecture seule, délai maximal, suppression du conteneur (`--rm` + `docker rm -f`).

## 10. Organisation du dépôt

```
config/reviewer.json          configuration (fournisseurs, critères, profils, limites)
src/main/java/fr/master/reviewer/
  ui/            interface Swing (aucune logique métier)
  application/   Facade (cas d'utilisation) + AppFactory (assemblage des objets)
  project/       import et représentation du projet (Composite)
  selection/     règles de sélection des fichiers (Strategy)
  analysis/      moteur, analyseurs LLM (Template Method), métriques, découpage
  analysis/deterministic/  analyseurs sans LLM + exécution en bac à sable
  llm/           abstraction LLM, adaptateur HTTP, décorateurs (retry, cache, trace), fabrique
  llm/prompt/    construction des prompts (Builder) et défenses anti-injection
  llm/parsing/   validation des réponses
  security/      Docker, politique de moindre privilège, masquage des secrets
  report/        génération LaTeX / HTML, compilation PDF
  persistence/   historique JSON
src/test/java/   80 tests (aucun appel LLM réel)
docker/          Dockerfiles du bac à sable et de LaTeX
scripts/         installation VM, construction des images, démo
examples/        projet d'exemple imparfait + rapport généré
rapport/         squelette du rapport technique
```

## 11. Dépannage

| Symptôme | Cause probable |
|---|---|
| `UNAVAILABLE : serveur injoignable` | serveur LM Studio non démarré ou mauvais port |
| `HTTP 400` avec LM Studio | mettre `"responseFormat": "none"` ; vérifier l'identifiant du modèle |
| `INVALID_SCHEMA` répété | modèle trop petit : essayer un modèle 7B+ instruct, baisser `maxCharsPerChunk` |
| `TIMEOUT` | modèle lent : augmenter `timeoutSeconds` |
| `la variable d'environnement … n'est pas définie` | exporter la clé puis relancer depuis ce terminal |
| Critère `build-and-tests` en échec « Docker indisponible » | comportement voulu hors VM ; voir section 9 |
