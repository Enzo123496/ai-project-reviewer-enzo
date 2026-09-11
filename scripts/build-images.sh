#!/usr/bin/env sh
# Construit les deux images Docker utilisées par l'application.
set -eu
cd "$(dirname "$0")/.."
docker build -t reviewer-sandbox:latest docker/sandbox
docker build -t reviewer-latex:latest docker/latex
echo "Images prêtes : reviewer-sandbox:latest, reviewer-latex:latest"
