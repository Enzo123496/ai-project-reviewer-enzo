# ---------------------------------------------------------------------------------------------
# Image de COMPILATION LaTeX isolée (utilisée quand report.pdfMode = "docker").
# Construire : docker build -t reviewer-latex:latest docker/latex
# Lancée par PdfCompiler avec : --network none, --read-only, --cap-drop ALL, limites, et
# pdflatex -no-shell-escape (LaTeX ne peut exécuter aucune commande système).
# ---------------------------------------------------------------------------------------------
FROM debian:12-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      texlive-latex-base texlive-latex-recommended texlive-latex-extra texlive-fonts-recommended lmodern \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 latex && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin latex
USER 10001:10001
WORKDIR /work
