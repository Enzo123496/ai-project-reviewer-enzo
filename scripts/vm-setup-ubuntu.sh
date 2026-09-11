#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# À exécuter DANS la machine virtuelle dédiée (Ubuntu 24.04 conseillé), jamais sur votre machine perso.
# Architecture visée :  machine hôte  ->  VM jetable  ->  Docker  ->  projet évalué
#
# Avant de lancer ce script, dans l'hyperviseur (VirtualBox, VMware, UTM, Hyper-V...) :
#   - désactiver les dossiers partagés et le presse-papiers/glisser-déposer partagés ;
#   - réseau en NAT (pas de pont) ; prendre un INSTANTANÉ (snapshot) propre pour revenir en arrière ;
#   - 4 Go de RAM et 2 vCPU suffisent.
# ---------------------------------------------------------------------------------------------
set -euo pipefail

sudo apt-get update
sudo apt-get install -y ca-certificates curl git openjdk-21-jdk maven uidmap dbus-user-session

# Docker Engine (dépôt officiel)
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-ce-rootless-extras

# Mode ROOTLESS : le démon Docker lui-même ne tourne pas en root (moindre privilège en profondeur).
# Si un conteneur s'échappait, l'attaquant ne serait que l'utilisateur de la VM, pas root.
sudo systemctl disable --now docker.service docker.socket || true
dockerd-rootless-setuptool.sh install
echo 'export DOCKER_HOST=unix:///run/user/$(id -u)/docker.sock' >> ~/.bashrc

echo "Terminé. Ouvrez un nouveau terminal puis : ./scripts/build-images.sh"
