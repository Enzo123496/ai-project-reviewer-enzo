# ---------------------------------------------------------------------------------------------
# Image d'EXÉCUTION ISOLÉE des projets évalués (code non fiable).
# Construire : docker build -t reviewer-sandbox:latest docker/sandbox
#
# Les restrictions (pas de réseau, lecture seule, non-root, limites CPU/mémoire/processus) sont
# appliquées au LANCEMENT par DockerCommandBuilder. Cette image se contente de :
#   - fournir Java + Maven dans des versions épinglées ;
#   - pré-télécharger les plugins/dépendances de test courants dans /opt/m2, pour que le build
#     puisse tourner SANS RÉSEAU (mvn -o) ;
#   - définir un utilisateur non-root.
# ---------------------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21

COPY warmup /tmp/warmup
RUN mvn -B -q -f /tmp/warmup/pom.xml -Dmaven.repo.local=/opt/m2 test \
 && rm -rf /tmp/warmup \
 && chmod -R a+rX /opt/m2

RUN groupadd --gid 10001 sandbox && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin sandbox
USER 10001:10001
WORKDIR /workspace
