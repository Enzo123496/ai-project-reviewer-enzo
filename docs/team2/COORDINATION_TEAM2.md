# Team 2 — Analysis Engine : périmètre, contrats et coordination

Document à partager avec tout le groupe. Il dit **ce que la Team 2 possède**, **ce qu'elle reçoit**, **ce qu'elle fournit**, **ce qui a changé** et **ce que chaque team doit faire**.

---

## 1. Périmètre

### Fichiers possédés par la Team 2

| Fichier | Rôle |
|---|---|
| `analysis/EvaluationEngine.java` | Orchestration des critères, isolation des échecs, note globale |
| `analysis/AnalyzerRegistry.java` | Choix de l'analyseur selon le critère, validation au démarrage |
| `analysis/Analyzer.java`, `AnalysisContext.java` | Contrat de tout analyseur |
| `analysis/AnalysisListener.java` | Événements de progression (Observer) |
| `analysis/AnalysisTrace.java`, `TraceSummary.java` | Traçabilité d'une analyse |
| `analysis/Criterion.java`, `CriterionResult.java`, `EvaluationResult.java` | Modèle des résultats |
| `analysis/AbstractLlmAnalyzer.java` | Squelette des analyses par LLM (Template Method) |
| `analysis/CodeChunkLlmAnalyzer.java`, `StructureLlmAnalyzer.java` | Analyse du code / de la structure |
| `analysis/Chunker.java`, `FileInterleaver.java` | Fragmentation du code, échantillon représentatif |
| `analysis/StructureSummarizer.java`, `JavaSourceCleaner.java` | Résumé d'architecture |
| `analysis/ResultAggregator.java`, `CitationChecker.java` | Agrégation, détection des fichiers inventés |
| `analysis/MetricsCalculator.java`, `ProjectMetrics.java`, `ContentReader.java` | Mesures déterministes |
| `analysis/SummaryWriter.java`, `LlmToolkit.java` | Synthèse, outils communs |
| `analysis/deterministic/TestPresenceAnalyzer`, `DocumentationAnalyzer`, `DockerfileAnalyzer`, `Checklist` | Critères sans LLM |
| `config/reviewer.json` — sections `criteria` et `profiles` | Définition des critères |

### Fichiers utilisés mais possédés par d'autres

| Fichier | Propriétaire |
|---|---|
| `llm/*` (fournisseurs, décorateurs), `llm/parsing/LlmResultParser`, `llm/prompt/PromptBuilder` (proposé) | Team 1 |
| `llm/prompt/UntrustedContentGuard`, `security/SecretRedactor`, `analysis/deterministic/SandboxedBuildAnalyzer` | Team 3 |
| `report/*`, `persistence/*` | Team 4 |
| `ui/*`, `testsupport/ScriptedLlmProvider` | Team 5 |
| `project/*`, `selection/*` (import du projet) | **non attribué — à décider** |

### Partage proposé entre Lucas et Enzo

| Moteur et critères | Pipeline d'analyse LLM |
|---|---|
| `EvaluationEngine`, `AnalyzerRegistry`, `AnalysisListener`, `AnalysisTrace` | `AbstractLlmAnalyzer`, `CodeChunkLlmAnalyzer`, `StructureLlmAnalyzer` |
| `MetricsCalculator`, analyseurs sans LLM, critères JSON | `Chunker`, `FileInterleaver`, `StructureSummarizer`, `JavaSourceCleaner` |
| `SummaryWriter`, modèle des résultats | `ResultAggregator`, `CitationChecker` |

---

## 2. Ce que la Team 2 reçoit

| De | Élément | Utilisation |
|---|---|---|
| Team 1 | `LlmResponse LlmProvider.ask(LlmRequest) throws LlmException` | appel du modèle (déjà décoré : retry, cache, repli) |
| Team 1 | `LlmException.Kind` | `CONFIGURATION` et `INTERRUPTED` arrêtent les segments suivants |
| Team 1 | `LlmEvaluation LlmResultParser.parse(String raw, Criterion c)` | validation ; `INVALID_SCHEMA` déclenche 1 demande de correction |
| Team 1 | `PromptBuilder.forCriterion(...)...build()`, `PromptBuilder.repairRequest(...)` | construction des requêtes |
| Team 3 | `List<String> UntrustedContentGuard.detectInjections(String file, String content)` | détections ajoutées en tête des problèmes |
| Team 3 | `String SecretRedactor.redact(String)` | appliqué au code avant envoi |
| Import | `Project`, `FileNode`, `FileSelectionStrategy.select(Project, Criterion)` | fichiers à analyser |
| Team 1 | `AppConfig.ContextSettings` (taille des segments, nombre max, parallélisme) | réglages |

---

## 3. Ce que la Team 2 fournit

### À la Team 1 (couche application)

```java
EvaluationResult EvaluationEngine.evaluate(EvaluationEngine.Session session);

record Session(Project project, List<Criterion> criteria, LlmProvider llm, String profileName,
               Map<String, String> configuration, AnalysisListener listener, AnalysisTrace trace,
               boolean llmCommentary);

List<String> AnalyzerRegistry.unknownAnalyzers(List<Criterion> criteria);   // à appeler au démarrage
```

### À la Team 3

```java
public interface Analyzer {
    CriterionResult analyze(AnalysisContext context);
}
// SandboxedBuildAnalyzer l'implémente ; en cas d'impossibilité : CriterionResult.failed(criterion, raison, source)
```

### À la Team 4 (rapport, historique)

`EvaluationResult` et `CriterionResult` sont **sérialisés dans l'historique JSON**. Tout changement de champ doit être annoncé.

| Champ de `CriterionResult` | Contenu |
|---|---|
| `score`, `maxScore` | note ; ne compte que si `status().isScored()` |
| `strengths`, `weaknesses`, `issues`, `recommendations` | textes issus en partie du LLM : **à échapper** |
| `status` | `OK`, `PARTIAL`, `FAILED`, **`NOT_APPLICABLE` (nouveau)** |
| `source` | méthode et modèle, ex. `LLM : mistral-7b (3/4 segment(s))` |
| `warnings` | avertissements de l'outil (couverture, niveau de résumé, fichiers inventés…) |

### À la Team 5 (IHM, tests)

```java
public interface AnalysisListener {
    default void analysisStarted(String projectName, int criteriaCount) { }
    default void criterionStarted(Criterion criterion, int index, int total) { }
    default void progress(Criterion criterion, String message) { }
    default void criterionFinished(CriterionResult result, int index, int total) { }
    default void warning(String message) { }
    default void analysisFinished(EvaluationResult result) { }
}
```

**Ces méthodes sont appelées depuis un thread de travail**, et depuis plusieurs threads à la fois si `parallelCriteria > 1`. En JavaFX, chaque mise à jour de l'écran doit passer par `Platform.runLater(...)`. Exemple :

```java
final class JavaFxAnalysisListener implements AnalysisListener {
    private final ProgressBar bar;
    private final ObservableList<CriterionResult> rows;

    @Override
    public void criterionFinished(CriterionResult r, int index, int total) {
        Platform.runLater(() -> {
            bar.setProgress((double) index / total);
            rows.add(r);
        });
    }
}
```

---

## 4. Changements de cette version

| # | Changement | Pourquoi (mesuré) | Impact sur les autres | Action |
|---|---|---|---|---|
| 1 | Répartition des fichiers **dossier par dossier** quand tout ne tient pas, avec message de **couverture** | À conditions identiques sur notre projet (86 fichiers, 14 dossiers) : 2 dossiers lus avant, 11 après | Team 4 : avertissement plus long dans le rapport | aucune |
| 2 | `Chunker.ChunkPlan` distingue `partiallySentFiles` et `skippedFiles` | un fichier coupé était annoncé « non envoyé » | aucun | aucune |
| 3 | **Moyenne pondérée** par la taille des segments (`ResultAggregator.Part`) | un segment de 300 caractères pesait autant qu'un de 12 000 | aucun | aucune |
| 4 | Statut **`NOT_APPLICABLE`** quand aucun fichier n'est pertinent, exclu de la note globale | un projet sans Java avait 0 et une note globale faussée | Team 4 : libellé « non applicable » (déjà ajouté dans `LatexReportGenerator`) ; Team 5 : afficher « — » si `!status().isScored()` | **Team 5** dans l'IHM JavaFX |
| 5 | **`CitationChecker`** : avertissement si le modèle cite un fichier inexistant | hallucinations non détectées | Team 4 : nouvel avertissement possible | aucune |
| 6 | **`StructureSummarizer` réécrit** : nettoyage des commentaires et chaînes, constructeurs, génériques, signatures multilignes, méthodes d'interface, dépendances intra-package, graphe des packages avec cycles, niveaux de détail | 42/83 fichiers dans le résumé ; dépendances intra-package invisibles | Team 1 : le contenu des prompts d'architecture change, donc les anciennes entrées de cache ne servent plus (normal) | aucune |
| 7 | `AnalyzerRegistry.unknownAnalyzers` appelé au démarrage dans `AppFactory` ; `Main` affiche l'erreur | une faute de frappe dans `reviewer.json` n'apparaissait qu'en pleine analyse | Team 1 : 6 lignes ajoutées dans `AppFactory`, 1 dans `Main` | **Team 1** : relire |
| 8 | Normalisation des apostrophes françaises et guillemets typographiques dans la déduplication | doublons « l'objet » / « l’objet » | aucun | aucune |

**Tests** : 22 tests ajoutés (102 au total, tous verts), dont les chemins jamais testés : échec de la demande de correction, arrêt sur erreur de configuration, parallélisme qui conserve l'ordre des critères.

---

## 5. Constats à transmettre

### Team 1 — cycles dans l'architecture

Le nouveau graphe des packages, appliqué à notre propre projet, révèle **4 cycles de dépendances** :

```
analysis <-> llm            llm (décorateurs) utilise AnalysisTrace, rangé dans analysis
analysis <-> llm.parsing    LlmResultParser utilise Criterion
analysis <-> llm.prompt     PromptBuilder utilise Criterion
analysis <-> selection      FileSelectionStrategy utilise Criterion
```

**Cause** : des types partagés par tout le monde (`Criterion`, `AnalysisTrace`, `TraceSummary`) sont rangés dans `analysis`, alors que des couches plus basses en ont besoin.

**Proposition** : créer un package `fr.master.reviewer.model` pour ces types et les résultats (`CriterionResult`, `EvaluationResult`, `ProjectMetrics`). Seuls les `import` changent. À faire **tôt**, en une seule fois, par la Team 1, une fois toutes les branches fusionnées. C'est aussi une excellente réponse à la question 9 du sujet (points de couplage), preuve à l'appui.

### Team 3 — faux positifs de détection d'injection

Appliquée à notre propre code, la détection produit **19 alertes**, toutes des faux positifs : les motifs eux-mêmes dans `UntrustedContentGuard` et les exemples des tests. Le 7ᵉ motif (`untrusted-data`) déclenche sur tout fichier qui mentionne ce mot. Comme les détections ne pénalisent pas la note (simple signalement), ce n'est pas bloquant, mais à mentionner dans les limites du rapport.

### Team 5 — tests et threads

- `ScriptedLlmProvider` (faux LLM) est désormais sous votre responsabilité : la Team 2 l'utilise dans `LlmAnalyzerTest`.
- Rappel `Platform.runLater` (section 3).

### Groupe — import du projet non attribué

`project/*` et `selection/*` (dossier, zip, Git, arborescence, règles d'exclusion) ne sont attribués à personne alors que le sujet l'exige (§3.1). Le moteur en dépend : à décider rapidement.

---

## 6. Décisions à valider en groupe

1. **`NOT_APPLICABLE` plutôt que 0** pour un critère sans fichier : d'accord ?
2. **Budgets** (`maxCharsPerChunk` 12 000, 4 segments, résumé 24 000) : à ajuster après les mesures avec LM Studio.
3. **Parallélisme** (`parallelCriteria` = 1 par défaut) : LM Studio traite souvent une requête à la fois.
4. **Agrégation de la sécurité** : garder la moyenne pondérée ou prendre la note **minimale** (une seule faille grave pèserait lourd) ? Non implémenté, proposition ouverte.

---

## 7. Vérifier

```bash
mvn test -Dtest='EvaluationEngineTest,LlmAnalyzerTest,ChunkerTest,ChunkerPartialTest,FileInterleaverTest,ResultAggregatorTest,CitationCheckerTest,JavaSourceCleanerTest,StructureSummarizerTest,AnalyzerRegistryTest,MetricsCalculatorTest,DeterministicAnalyzersTest'
java -jar target/ai-project-reviewer-1.0.0.jar --cli --project examples/sample-bank-project --profile complet --provider mock
```

## 8. Texte de pull request

> **Team 2 — Améliorations du moteur d'analyse**
>
> Échantillon représentatif quand le code dépasse le budget (couverture affichée), moyenne pondérée, statut `NOT_APPLICABLE`, détection des fichiers inventés par le LLM, résumé d'architecture réécrit (dépendances intra-package, graphe et cycles de packages, niveaux de détail), validation des analyseurs au démarrage. 22 tests ajoutés, 102/102 verts.
>
> **À relire** : Team 1 (`AppFactory`, `Main`), Team 4 (`LatexReportGenerator` : libellé du nouveau statut), Team 5 (`ResultsTableModel` : à reproduire dans l'IHM JavaFX).
>
> **Constats** : 4 cycles de packages (voir §5), 19 faux positifs de détection d'injection sur notre propre code.
