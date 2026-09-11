Rapport produit automatiquement par l'application (ligne de commande, profil `complet`) sur `examples/sample-bank-project`.

**Attention :** il a été généré avec le fournisseur `mock` (réponses simulées), comme indiqué dans le rapport lui-même.
Il montre la structure du document, la détection de l'injection de prompt et les analyses déterministes,
mais les notes des critères LLM n'ont aucune valeur. Le sujet exige un rapport issu d'une vraie analyse :
régénérez-le avec LM Studio :

    java -jar target/ai-project-reviewer-1.0.0.jar --cli --project examples/sample-bank-project --profile complet --provider lmstudio
