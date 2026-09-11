#!/usr/bin/env sh
# Démo complète SANS serveur LLM : fournisseur "mock" (réponses simulées, notes sans valeur réelle).
set -eu
cd "$(dirname "$0")/.."
mvn -q -DskipTests package
java -jar target/ai-project-reviewer-1.0.0.jar --cli --project examples/sample-bank-project --profile complet --provider mock
